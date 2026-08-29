package com.tzevabest.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.web.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Приём вебхуков доставки от провайдеров (T-12.3). Эндпоинт публичный, поэтому обязательны проверка
 * подписи (HMAC тела) и защита от повторов (event_id в Redis с TTL) — по образцу защиты публичной
 * формы заявки. Обновляет статус в notifications_log по provider_message_id.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationWebhookService {

    private static final Duration REPLAY_TTL = Duration.ofDays(2);

    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    @Value("${app.notification.signing-secret}")
    private String secret;

    /** Проверяет подпись и повтор, применяет событие. Бросает 401 при неверной подписи. */
    public void handle(String provider, String rawBody, String signature) {
        if (!HmacSigner.matches(HmacSigner.hexHmac(secret, rawBody), signature)) {
            throw new BusinessException("notification.webhook.invalid_signature", HttpStatus.UNAUTHORIZED);
        }
        JsonNode payload = parse(rawBody);
        String eventId = text(payload, "event_id");
        if (eventId != null) {
            Boolean fresh = redis.opsForValue()
                    .setIfAbsent("notif:webhook:evt:" + provider + ":" + eventId, "1", REPLAY_TTL);
            if (!Boolean.TRUE.equals(fresh)) {
                log.debug("Duplicate delivery webhook {} from {}", eventId, provider);
                return; // повтор — молча игнорируем
            }
        }
        apply(text(payload, "message_id"), text(payload, "status"), text(payload, "error"));
    }

    private void apply(String messageId, String status, String error) {
        if (messageId == null || status == null) {
            return;
        }
        // delivered оставляет SENT (доставлено), failed/bounced → FAILED с причиной.
        if (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("bounced")) {
            jdbc.update("update notifications_log set status = 'FAILED', error_message = ?, updated_at = now() "
                    + "where provider_message_id = ?", error, messageId);
        } else {
            jdbc.update("update notifications_log set updated_at = now() where provider_message_id = ?", messageId);
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("notification.webhook.malformed", HttpStatus.BAD_REQUEST);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
