package com.tzevabest.notification.service.webhook;

import java.util.Map;

/**
 * Проверка подписи входящего вебхука доставки для КОНКРЕТНОГО провайдера (R-60).
 * <p>
 * У каждого провайдера своя схема подписи, и они несовместимы: Twilio считает HMAC-SHA1 от URL и
 * отсортированных параметров (base64, {@code X-Twilio-Signature}); Mailgun — HMAC-SHA256 от
 * конкатенации timestamp и token; SendGrid — ECDSA с публичным ключом. Единая «своя» схема не подошла
 * бы ни одному, поэтому проверка вынесена за интерфейс, а реализация выбирается по {@code {provider}}
 * из пути. Схемы задокументированы в docs/notifications-webhooks.md.
 */
public interface WebhookVerifier {

    /** Имя провайдера в пути {@code /webhooks/notifications/{provider}} (в нижнем регистре). */
    String provider();

    /**
     * Подтверждает подлинность тела по заголовкам/полезной нагрузке провайдера.
     *
     * @param rawBody сырое тело запроса (подпись считается именно по нему, до парсинга)
     * @param headers заголовки запроса, имена приведены к нижнему регистру
     */
    boolean verify(String rawBody, Map<String, String> headers);
}
