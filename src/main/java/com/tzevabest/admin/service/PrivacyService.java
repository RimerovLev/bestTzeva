package com.tzevabest.admin.service;

import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.media.service.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final StringRedisTemplate redis;

    @Value("${app.privacy.photo-retention:P365D}")
    private Duration photoRetention;

    /** Сборка всех данных по телефону или email в один объект. Факт доступа пишется в audit_log. */
    @Transactional
    public Map<String, Object> exportSubject(String contact) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contact", contact);
        data.put("product_requests", jdbc.queryForList(
                "select * from product_requests where customer_phone = ? or customer_email = ?", contact, contact));
        data.put("product_request_items", jdbc.queryForList(
                "select i.* from product_request_items i join product_requests r on r.id = i.request_id "
                        + "where r.customer_phone = ? or r.customer_email = ?", contact, contact));
        data.put("service_requests", jdbc.queryForList(
                "select * from service_requests where customer_phone = ? or customer_email = ?", contact, contact));
        data.put("service_request_media", jdbc.queryForList(
                "select m.id, m.request_id, m.object_key, m.type from service_request_media m "
                        + "join service_requests s on s.id = m.request_id "
                        + "where s.customer_phone = ? or s.customer_email = ?", contact, contact));
        data.put("consents", jdbc.queryForList("select * from consents where contact = ?", contact));
        data.put("notifications", jdbc.queryForList(
                "select id, channel, template_code, status, created_at from notifications_log where recipient = ?",
                contact));
        auditAccess("privacy.export", contact);
        return data;
    }

    /** Обезличивает заявки по контакту (имя/телефон/email/адрес → маркер); строки и позиции сохраняются. */
    @Transactional
    public int anonymize(String contact) {
        int product = jdbc.update("update product_requests set customer_name = ?, customer_phone = ?, "
                + "customer_email = null, comment = null where customer_phone = ? or customer_email = ?",
                MARKER, MARKER, contact, contact);
        int service = jdbc.update("update service_requests set customer_name = ?, customer_phone = ?, "
                + "customer_email = null, address = null, comment = null "
                + "where customer_phone = ? or customer_email = ?", MARKER, MARKER, contact, contact);
        auditAccess("privacy.anonymize", contact);
        return product + service;
    }

    /**
     * Удаляет фото помещений из завершённых/отклонённых заявок старше срока — и объекты в S3, и строки.
     * Под распределённой блокировкой (как уборка резервов по R-42).
     */
    @Transactional
    public int purgeExpiredPhotos() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return 0;
        }
        try {
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
        } finally {
            redis.delete(LOCK_KEY);
        }
    }

    private void auditAccess(String action, String contact) {
        // Контакт в аудите маскируется санитайзером AuditService (телефон → ***last4).
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                action, "subject", null, Map.of("contact_phone", contact));
    }
}
