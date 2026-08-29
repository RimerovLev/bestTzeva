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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-12.3: вебхук доставки (подпись, повтор, обновление статуса) и отписка (запись отзыва MARKETING,
 * подавление нетранзакционных, транзакционные не подавляются).
 */
class NotificationDeliveryTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UnsubscribeService unsubscribe;
    @Value("${app.notification.signing-secret}")
    private String secret;

    // ── Вебхук доставки ───────────────────────────────────────────────────────────

    @Test
    void webhook_updates_status_verifies_signature_and_ignores_replay() {
        seedSent("pm-1", "a@x.test");

        String failBody = "{\"event_id\":\"e1\",\"message_id\":\"pm-1\",\"status\":\"failed\",\"error\":\"bounced\"}";
        assertThat(postWebhook("sendgrid", failBody, hmac(failBody)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-1")).isEqualTo("FAILED");
        assertThat(errorOf("pm-1")).contains("bounced");

        // неверная подпись → 401, статус не меняется
        seedSent("pm-2", "b@x.test");
        String body2 = "{\"event_id\":\"e2\",\"message_id\":\"pm-2\",\"status\":\"failed\"}";
        assertThat(postWebhook("sendgrid", body2, "deadbeef").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf("pm-2")).isEqualTo("SENT");

        // повтор того же event_id с другим статусом — игнорируется, строка остаётся FAILED
        String replay = "{\"event_id\":\"e1\",\"message_id\":\"pm-1\",\"status\":\"delivered\"}";
        assertThat(postWebhook("sendgrid", replay, hmac(replay)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("pm-1")).isEqualTo("FAILED");
    }

    // ── Отписка ───────────────────────────────────────────────────────────────────

    @Test
    void unsubscribe_records_optout_and_suppresses_only_informational() {
        String contact = "client@example.com";
        String link = unsubscribe.unsubscribeLink(contact);
        String token = link.substring(link.indexOf("token=") + "token=".length());

        ResponseEntity<String> resp = rest.getForEntity(
                "/api/v1/notifications/unsubscribe?token=" + token, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("unsubscribed");

        // отзыв записан в consents (MARKETING granted=false)
        Boolean granted = jdbcTemplate.queryForObject(
                "select granted from consents where contact = ? and consent_type = 'MARKETING' "
                        + "order by id desc limit 1", Boolean.class, contact);
        assertThat(granted).isFalse();

        assertThat(unsubscribe.isOptedOut(contact)).isTrue();
        assertThat(unsubscribe.suppresses(false, contact)).isTrue();  // информационное — подавляется
        assertThat(unsubscribe.suppresses(true, contact)).isFalse();  // транзакционное — нет
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
        headers.set("X-Signature", signature);
        return rest.exchange("/api/v1/webhooks/notifications/" + provider, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String statusOf(String pmid) {
        return jdbcTemplate.queryForObject(
                "select status from notifications_log where provider_message_id = ?", String.class, pmid);
    }

    private String errorOf(String pmid) {
        return jdbcTemplate.queryForObject(
                "select error_message from notifications_log where provider_message_id = ?", String.class, pmid);
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
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
