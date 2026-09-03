package com.tzevabest.notification.service.webhook;

import com.tzevabest.notification.service.HmacSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Собственная схема подписи ({@code provider = internal}) для наших интеграций и тестов: HMAC-SHA256
 * от сырого тела в hex, сверяется с заголовком {@code X-Signature}. Секрет — {@code webhook-secret}
 * (R-66: отдельный от секрета отписки).
 */
@Component
public class InternalWebhookVerifier implements WebhookVerifier {

    private final String secret;

    public InternalWebhookVerifier(@Value("${app.notification.webhook-secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public String provider() {
        return "internal";
    }

    @Override
    public boolean verify(String rawBody, Map<String, String> headers) {
        String signature = headers.get("x-signature");
        return HmacSigner.matches(HmacSigner.hexHmac(secret, rawBody), signature);
    }
}
