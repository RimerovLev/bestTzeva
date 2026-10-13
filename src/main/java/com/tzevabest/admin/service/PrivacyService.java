package com.tzevabest.admin.service;

import com.tzevabest.common.lock.DistributedLock;
import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.media.service.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Работа с персональными данными (T-14.2): экспорт данных субъекта, анонимизация с сохранением
 * обезличенных заявок для отчётности, ретеншен фото помещений (удаление объектов в S3, не только
 * строк). Экспорт и анонимизация — доступ персонала к ПДн — пишутся в audit_log.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PrivacyService {

    private static final String MARKER = "[deleted]";
    private static final String LOCK_KEY = "privacy:photo-purge:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final MediaStorage mediaStorage;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final DistributedLock lock;

    @Value("${app.privacy.photo-retention:P365D}")
    private Duration photoRetention;

    /** Соль проекта для псевдонимизации контактов при анонимизации (R-59/R-64). */
    @Value("${app.privacy.pseudonym-secret:dev-only-pseudonym-secret-change-me}")
    private String pseudonymSecret;

    /** Сборка всех данных по телефону или email в один объект. Факт доступа пишется в audit_log. */
    @Transactional
    public Map<String, Object> exportSubject(String typed) {
        String contact = canonical(typed);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contact", contact);
        data.put("product_requests", jdbc.queryForList(
                "select * from product_requests where customer_phone = ? or lower(customer_email) = ?", contact, contact));
        data.put("product_request_items", jdbc.queryForList(
                "select i.* from product_request_items i join product_requests r on r.id = i.request_id "
                        + "where r.customer_phone = ? or lower(r.customer_email) = ?", contact, contact));
        data.put("service_requests", jdbc.queryForList(
                "select * from service_requests where customer_phone = ? or lower(customer_email) = ?", contact, contact));
        data.put("service_request_media", jdbc.queryForList(
                "select m.id, m.request_id, m.object_key, m.type from service_request_media m "
                        + "join service_requests s on s.id = m.request_id "
                        + "where s.customer_phone = ? or lower(s.customer_email) = ?", contact, contact));
        data.put("consents", jdbc.queryForList("select * from consents where lower(contact) = ?", contact));
        data.put("notifications", jdbc.queryForList(
                "select id, channel, template_code, status, created_at from notifications_log where lower(recipient) = ?",
                contact));
        auditAccess("privacy.export", contact);
        return data;
    }

    /**
     * Обезличивает по контакту все таблицы, где он хранится (полный перечень — в docs/privacy.md):
     * <ul>
     *   <li>заявки {@code product_requests}/{@code service_requests} — имя/телефон/email/адрес → маркер,
     *       строки и позиции сохраняются для отчётности по спросу;</li>
     *   <li>{@code notifications_log} — {@code recipient} → маркер, снимок тела/темы/ошибки очищается;
     *       остаются template_code, канал, статус и время (доказательство факта отправки, R-64);</li>
     *   <li>{@code consents} — {@code contact} → непрозрачный псевдоним; тип согласия, версия политики и
     *       время сохраняются (запись согласия — доказательство законности обработки, стирать нельзя).</li>
     * </ul>
     * Полного контакта после этого не остаётся ни в одной таблице, включая {@code audit_log}, куда
     * телефон/email пишутся только маскированными (R-59).
     */
    @Transactional
    public int anonymize(String typed) {
        String contact = canonical(typed);
        int product = jdbc.update("update product_requests set customer_name = ?, customer_phone = ?, "
                + "customer_email = null, comment = null where customer_phone = ? or lower(customer_email) = ?",
                MARKER, MARKER, contact, contact);
        int service = jdbc.update("update service_requests set customer_name = ?, customer_phone = ?, "
                + "customer_email = null, address = null, comment = null "
                + "where customer_phone = ? or lower(customer_email) = ?", MARKER, MARKER, contact, contact);
        // Журнал уведомлений: контакт и снимок сообщения — персональные данные; факт отправки остаётся.
        jdbc.update("update notifications_log set recipient = ?, subject = null, body_snapshot = null, "
                + "error_message = null, updated_at = now() where lower(recipient) = ?", MARKER, contact);
        // consents append-only: контакт заменяем псевдонимом санкционированным UPDATE (V48).
        jdbc.execute("set local tzevabest.consent_anonymize = 'on'");
        jdbc.update("update consents set contact = ? where lower(contact) = ?", pseudonym(contact), contact);
        auditAccess("privacy.anonymize", contact);
        return product + service;
    }

    /**
     * Контакт в том виде, в каком он лежит в таблицах. Телефоны заявок хранятся в E.164 (+972…), а
     * сотрудник вводит номер как привык — 050-111-9999; распознанный номер приводим к E.164, иначе
     * экспорт молча вернул бы пустоту, а анонимизация — 0. Email сравниваем без учёта регистра.
     */
    static String canonical(String contact) {
        String c = contact.trim();
        return c.contains("@") ? c.toLowerCase(Locale.ROOT) : PhoneNormalizer.normalizeForRateKey(c);
    }

    /** Стабильный непрозрачный псевдоним контакта: SHA-256 от соли проекта и контакта, 16 hex. */
    private String pseudonym(String contact) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((pseudonymSecret + ":" + contact)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("anon:");
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((hash[i] >> 4) & 0xF, 16))
                        .append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Удаляет фото помещений из завершённых/отклонённых заявок старше срока — и объекты в S3, и строки.
     * Под распределённой блокировкой (как уборка резервов по R-42).
     */
    @Transactional
    public int purgeExpiredPhotos() {
        return lock.runIfAcquired(LOCK_KEY, LOCK_TTL, handle -> {
            long seconds = photoRetention.getSeconds();
            List<Map<String, Object>> expired = jdbc.queryForList(
                    "select m.id, m.object_key from service_request_media m "
                            + "join service_requests s on s.id = m.request_id "
                            + "where s.status in ('COMPLETED','REJECTED') "
                            + "and s.updated_at < now() - (? || ' seconds')::interval", seconds);
            for (Map<String, Object> row : expired) {
                String key = (String) row.get("object_key");
                if (key != null) {
                    mediaStorage.deletePrivate(key); // объект в S3, не только строка
                }
                jdbc.update("delete from service_request_media where id = ?", row.get("id"));
            }
            if (!expired.isEmpty()) {
                audit.record(null, null, "privacy.photo_purge", "service_request_media", null,
                        Map.of("deleted", expired.size()));
                log.info("Privacy photo retention: purged {} photo(s)", expired.size());
            }
            return expired.size();
        }, 0);
    }

    private void auditAccess(String action, String contact) {
        // Контакт в аудите маскируется санитайзером AuditService (телефон → ***last4).
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                action, "subject", null, Map.of("contact_phone", contact));
    }
}
