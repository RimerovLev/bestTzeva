package com.tzevabest.notification;

import com.tzevabest.notification.service.UnsubscribeService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-12.3: вебхук доставки (подпись по провайдеру R-60, повтор R-72, обновление статуса) и отписка
 * (POST меняет состояние, GET — только подтверждение R-63; токен непрозрачный и со сроком R-62).
 */
class NotificationDeliveryTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UnsubscribeService unsubscribe;
    @Value("${app.notification.webhook-secret}")
    private String webhookSecret;

    // ── Вебхук доставки ───────────────────────────────────────────────────────────

    @Test
    void internal_webhook_updates_status_verifies_signature_and_ignores_replay() {
        seedSent("pm-1", "a@x.test");

        String failBody = "{\"event_id\":\"e1\",\"message_id\":\"pm-1\",\"status\":\"failed\",\"error\":\"bounced\"}";
        assertThat(postWebhook("internal", failBody, hmac(failBody)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-1")).isEqualTo("FAILED");
        assertThat(errorOf("pm-1")).contains("bounced");

        // неверная подпись → 401, статус не меняется
        seedSent("pm-2", "b@x.test");
        String body2 = "{\"event_id\":\"e2\",\"message_id\":\"pm-2\",\"status\":\"failed\"}";
        assertThat(postWebhook("internal", body2, "deadbeef").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf("pm-2")).isEqualTo("SENT");

        // повтор того же event_id с другим статусом — игнорируется, строка остаётся FAILED
        String replay = "{\"event_id\":\"e1\",\"message_id\":\"pm-1\",\"status\":\"delivered\"}";
        assertThat(postWebhook("internal", replay, hmac(replay)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-1")).isEqualTo("FAILED");
    }

    @Test
    void unknown_provider_is_rejected() {
        String body = "{\"event_id\":\"x\",\"message_id\":\"pm\",\"status\":\"failed\"}";
        assertThat(postWebhook("sendgrid", body, hmac(body)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhook_without_event_id_and_timestamp_is_rejected() {
        // R-72: без event_id и без метки времени подписанное тело можно крутить вечно — отклоняем.
        seedSent("pm-3", "c@x.test");
        String body = "{\"message_id\":\"pm-3\",\"status\":\"failed\"}";
        assertThat(postWebhook("internal", body, hmac(body)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhook_without_event_id_but_with_timestamp_dedups_by_body_hash() {
        seedSent("pm-4", "d@x.test");
        String body = "{\"timestamp\":\"1735000000\",\"message_id\":\"pm-4\",\"status\":\"failed\",\"error\":\"soft\"}";
        assertThat(postWebhook("internal", body, hmac(body)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-4")).isEqualTo("FAILED");
        // повтор того же тела в пределах окна — молча игнорируется (200, состояние не двоится)
        assertThat(postWebhook("internal", body, hmac(body)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void mailgun_webhook_verifies_by_its_documented_scheme() {
        // R-60: Mailgun подписывает hex(HMAC-SHA256(secret, timestamp + token)); подпись — в теле.
        seedSent("pm-mg", "mg@x.test");
        String ts = Long.toString(java.time.Instant.now().getEpochSecond()); // свежий timestamp (окно 5 мин)
        String token = "tok-" + System.nanoTime();
        String signature = hmac(ts + token); // именно timestamp+token, как в документации Mailgun
        String body = "{\"signature\":{\"timestamp\":\"" + ts + "\",\"token\":\"" + token + "\",\"signature\":\""
                + signature + "\"},\"event_id\":\"mg-1\",\"message_id\":\"pm-mg\",\"status\":\"failed\",\"error\":\"hard\"}";
        assertThat(postWebhook("mailgun", body, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-mg")).isEqualTo("FAILED");

        // подделанная подпись → 401
        String forged = body.replace(signature, "00" + signature.substring(2));
        seedSent("pm-mg2", "mg2@x.test");
        String forged2 = forged.replace("pm-mg", "pm-mg2").replace("mg-1", "mg-2");
        assertThat(postWebhook("mailgun", forged2, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf("pm-mg2")).isEqualTo("SENT");
    }

    @Test
    void mailgun_stale_timestamp_and_token_replay_are_rejected() {
        // Аудит п.10: подпись покрывает только timestamp+token. Старый timestamp и повтор token
        // (даже с валидной подписью, но ДРУГИМ телом) должны отклоняться.
        seedSent("pm-old", "old@x.test");
        String oldTs = "1735000001"; // 2024 — вне окна
        String oldTok = "tok-old-" + System.nanoTime();
        String oldBody = "{\"signature\":{\"timestamp\":\"" + oldTs + "\",\"token\":\"" + oldTok
                + "\",\"signature\":\"" + hmac(oldTs + oldTok)
                + "\"},\"event_id\":\"old-1\",\"message_id\":\"pm-old\",\"status\":\"failed\"}";
        assertThat(postWebhook("mailgun", oldBody, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf("pm-old")).isEqualTo("SENT"); // не тронут

        // повтор token с поддельным телом
        seedSent("pm-r1", "r1@x.test");
        seedSent("pm-r2", "r2@x.test");
        String ts = Long.toString(java.time.Instant.now().getEpochSecond());
        String tok = "tok-replay-" + System.nanoTime();
        String sig = hmac(ts + tok);
        String first = "{\"signature\":{\"timestamp\":\"" + ts + "\",\"token\":\"" + tok + "\",\"signature\":\""
                + sig + "\"},\"event_id\":\"r-1\",\"message_id\":\"pm-r1\",\"status\":\"failed\"}";
        assertThat(postWebhook("mailgun", first, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // та же подпись/token, другое тело (message_id) — token уже израсходован → 401
        String replay = "{\"signature\":{\"timestamp\":\"" + ts + "\",\"token\":\"" + tok + "\",\"signature\":\""
                + sig + "\"},\"event_id\":\"r-2\",\"message_id\":\"pm-r2\",\"status\":\"failed\"}";
        assertThat(postWebhook("mailgun", replay, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf("pm-r2")).isEqualTo("SENT");
    }

    // ── Отписка ───────────────────────────────────────────────────────────────────

    @Test
    void get_confirms_without_changing_state_post_unsubscribes() {
        String contact = "client@example.com";
        String token = tokenFrom(unsubscribe.unsubscribeLink(contact));

        // GET только подтверждает — состояние не меняется (R-63): сканеры ссылок не отпишут.
        ResponseEntity<String> confirm = rest.getForEntity(
                "/api/v1/notifications/unsubscribe?token=" + token, String.class);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirm.getBody()).contains("confirm_required");
        assertThat(consentCount(contact)).isZero(); // ни одной записи после GET

        // POST — фактическая отписка
        ResponseEntity<String> done = rest.exchange("/api/v1/notifications/unsubscribe?token=" + token,
                HttpMethod.POST, new HttpEntity<>(null), String.class);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(done.getBody()).contains("unsubscribed");

        Boolean granted = jdbcTemplate.queryForObject(
                "select granted from consents where contact = ? and consent_type = 'MARKETING' "
                        + "order by id desc limit 1", Boolean.class, contact);
        assertThat(granted).isFalse();
        assertThat(unsubscribe.isOptedOut(contact)).isTrue();
        assertThat(unsubscribe.suppresses(false, contact)).isTrue();  // информационное — подавляется
        assertThat(unsubscribe.suppresses(true, contact)).isFalse();  // транзакционное — нет
    }

    @Test
    void repeated_get_creates_no_consent_rows() {
        String contact = "scanned@example.com";
        String token = tokenFrom(unsubscribe.unsubscribeLink(contact));
        for (int i = 0; i < 3; i++) {
            rest.getForEntity("/api/v1/notifications/unsubscribe?token=" + token, String.class);
        }
        assertThat(consentCount(contact)).isZero();
    }

    @Test
    void token_does_not_contain_recoverable_address() {
        // R-62: адрес не должен встречаться в токене ни в открытом, ни в base64-виде.
        String contact = "secret-addr@example.com";
        String token = tokenFrom(unsubscribe.unsubscribeLink(contact));
        assertThat(token).doesNotContain(contact);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(contact.getBytes(StandardCharsets.UTF_8));
        assertThat(token).doesNotContain(b64);
    }

    @Test
    void invalid_unsubscribe_token_is_rejected() {
        assertThat(rest.getForEntity("/api/v1/notifications/unsubscribe?token=bogus.sig", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void seedSent(String providerMessageId, String recipient) {
        jdbcTemplate.update("""
                insert into notifications_log (channel, template_code, recipient, status, priority,
                        attempt_count, provider_message_id, sent_at)
                values ('EMAIL', 'product_request_received', ?, 'SENT', 'NORMAL', 1, ?, now())""",
                recipient, providerMessageId);
    }

    private ResponseEntity<String> postWebhook(String provider, String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signature != null) {
            headers.set("X-Signature", signature);
        }
        return rest.exchange("/api/v1/webhooks/notifications/" + provider, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String tokenFrom(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private int consentCount(String contact) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(*) from consents where contact = ?", Integer.class, contact);
        return n == null ? 0 : n;
    }

    private String statusOf(String pmid) {
        return jdbcTemplate.queryForObject(
                "select status from notifications_log where provider_message_id = ?", String.class, pmid);
    }

    private String errorOf(String pmid) {
        return jdbcTemplate.queryForObject(
                "select error_message from notifications_log where provider_message_id = ?", String.class, pmid);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
