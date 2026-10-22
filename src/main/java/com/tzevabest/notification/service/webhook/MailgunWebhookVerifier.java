package com.tzevabest.notification.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.notification.service.HmacSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Проверка вебхука Mailgun по его документированной схеме (R-60):
 * {@code signature = hex(HMAC-SHA256(webhook-secret, timestamp + token))}, где timestamp, token и
 * signature приходят в JSON-объекте {@code signature} тела. См. docs/notifications-webhooks.md и
 * https://documentation.mailgun.com/docs/mailgun/user-manual/events/#securing-webhooks .
 */
@Component
@Slf4j
public class MailgunWebhookVerifier implements WebhookVerifier {

    /** Подпись Mailgun покрывает только timestamp+token, поэтому окно её пригодности держим узким. */
    private static final long TOLERANCE_SECONDS = 300;

    private final String secret;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;

    public MailgunWebhookVerifier(@Value("${app.notification.webhook-secret}") String secret,
                                  ObjectMapper json, StringRedisTemplate redis) {
        this.secret = secret;
        this.json = json;
        this.redis = redis;
    }

    @Override
    public String provider() {
        return "mailgun";
    }

    @Override
    public boolean verify(String rawBody, Map<String, String> headers) {
        try {
            JsonNode sig = json.readTree(rawBody).path("signature");
            String timestamp = sig.path("timestamp").asText(null);
            String token = sig.path("token").asText(null);
            String signature = sig.path("signature").asText(null);
            if (timestamp == null || token == null || signature == null) {
                return false;
            }
            // 1) Узкое временное окно: старую подсмотренную тройку нельзя переигрывать спустя время (R-?? / аудит п.10).
            long ts;
            try {
                ts = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                return false;
            }
            if (Math.abs(Instant.now().getEpochSecond() - ts) > TOLERANCE_SECONDS) {
                return false;
            }
            // 2) Подпись.
            if (!HmacSigner.matches(HmacSigner.hexHmac(secret, timestamp + token), signature)) {
                return false;
            }
            // 3) token одноразовый: даже валидную подпись нельзя использовать дважды (как рекомендует Mailgun).
            Boolean fresh = redis.opsForValue().setIfAbsent(
                    "notif:webhook:mailgun:tok:" + token, "1", Duration.ofSeconds(TOLERANCE_SECONDS * 2));
            return Boolean.TRUE.equals(fresh);
        } catch (Exception e) {
            log.debug("Mailgun webhook body unparseable", e);
            return false;
        }
    }
}
