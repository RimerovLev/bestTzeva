package com.tzevabest.notification.service;

import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Отписка от рассылок (T-12.3). Ссылка в информационных письмах ведёт на эндпоинт с непрозрачным
 * токеном; отписка пишется в consents как отзыв MARKETING-согласия. Транзакционные письма (статус
 * заявки, сброс пароля) не отписываются.
 * <p>
 * Токен (R-62) — не {@code base64(contact).hmac}: адрес НЕ хранится в открытом (обратимом) виде и не
 * попадает в логи/Referer/историю. Внутри — {@code AES-GCM(unsubscribe-secret)} над полезной нагрузкой
 * {@code срок_действия || contact}: шифр скрывает адрес, GCM даёт целостность (отдельный HMAC не
 * нужен), случайный nonce делает токен разным в каждом письме, а срок действия ограничивает окно
 * пригодности. Секрет — отдельный от секрета вебхуков (R-66).
 */
@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConsentService consents;
    private final JdbcTemplate jdbc;

    @Value("${app.notification.unsubscribe-secret}")
    private String secret;

    @Value("${app.notification.unsubscribe-ttl:P30D}")
    private Duration ttl;

    // Ссылка ведёт на страницу витрины (там кнопка подтверждения → POST), а не на GET API, который
    // открывал бы сырой JSON и срабатывал бы на сканерах ссылок (аудит п.7).
    @Value("${app.web.storefront-url:http://localhost:3000}")
    private String storefrontUrl;

    /** Ссылка отписки для получателя (email): страница витрины с непрозрачным токеном и сроком. */
    public String unsubscribeLink(String contact) {
        return storefrontUrl + "/unsubscribe?token=" + issue(contact);
    }

    /** Обрабатывает подтверждённую отписку (POST): проверяет токен и пишет отзыв MARKETING-согласия. */
    @Transactional
    public void unsubscribe(String token, String ip) {
        String contact = verify(token);
        consents.recordMarketing(null, contact, false, ip, "notification_unsubscribe");
    }

    /** Проверяет токен, не меняя состояние (для страницы подтверждения на GET). Бросает при невалидном. */
    public void validate(String token) {
        verify(token);
    }

    /** Отписан ли контакт (последняя MARKETING-запись — отзыв). */
    public boolean isOptedOut(String contact) {
        Boolean granted = jdbc.query(
                "select granted from consents where contact = ? and consent_type = 'MARKETING' "
                        + "order by created_at desc, id desc limit 1",
                rs -> rs.next() ? rs.getBoolean("granted") : null, contact);
        return Boolean.FALSE.equals(granted);
    }

    /** Подавлять ли отправку: только нетранзакционное уведомление отписавшемуся. */
    public boolean suppresses(boolean transactional, String contact) {
        return !transactional && isOptedOut(contact);
    }

    // ── токен ─────────────────────────────────────────────────────────────────────

    private String issue(String contact) {
        try {
            byte[] contactBytes = contact.getBytes(StandardCharsets.UTF_8);
            ByteBuffer plain = ByteBuffer.allocate(Long.BYTES + contactBytes.length);
            plain.putLong(Instant.now().plus(ttl).getEpochSecond());
            plain.put(contactBytes);

            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plain.array());

            ByteBuffer out = ByteBuffer.allocate(nonce.length + ct.length);
            out.put(nonce).put(ct);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot issue unsubscribe token", e);
        }
    }

    private String verify(String token) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            if (raw.length <= NONCE_BYTES) {
                throw new ValidationException("notification.unsubscribe.invalid_token");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(raw, 0, nonce, 0, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(raw, NONCE_BYTES, raw.length - NONCE_BYTES);

            ByteBuffer buf = ByteBuffer.wrap(plain);
            long expiry = buf.getLong();
            if (Instant.now().getEpochSecond() > expiry) {
                throw new ValidationException("notification.unsubscribe.invalid_token"); // просрочен
            }
            byte[] contactBytes = new byte[buf.remaining()];
            buf.get(contactBytes);
            return new String(contactBytes, StandardCharsets.UTF_8);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            // повреждённый токен, неверная подпись GCM, неверный формат — единый ответ
            throw new ValidationException("notification.unsubscribe.invalid_token");
        }
    }

    /** 256-битный ключ AES из секрета (SHA-256 секрета). */
    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
