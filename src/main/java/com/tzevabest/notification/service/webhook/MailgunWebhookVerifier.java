package com.tzevabest.notification.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.notification.service.HmacSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    private final String secret;
    private final ObjectMapper json;

    public MailgunWebhookVerifier(@Value("${app.notification.webhook-secret}") String secret, ObjectMapper json) {
        this.secret = secret;
        this.json = json;
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
            return HmacSigner.matches(HmacSigner.hexHmac(secret, timestamp + token), signature);
        } catch (Exception e) {
            log.debug("Mailgun webhook body unparseable", e);
            return false;
        }
    }
}
