package com.tzevabest.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.notification.service.webhook.WebhookVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Приём вебхуков доставки от провайдеров (T-12.3). Эндпоинт публичный, поэтому обязательны проверка
 * подписи и защита от повторов.
 * <p>
 * Подпись проверяет {@link WebhookVerifier}, выбранный по {@code {provider}} из пути — у каждого
 * провайдера своя схема (R-60). Неизвестный провайдер отклоняется как неподдерживаемый (а не молча
 * принимается). Защита от повторов: по {@code event_id}, а при его отсутствии — по хешу тела в
 * пределах временного окна; тело без {@code event_id} и без метки времени отклоняется (R-72).
 */
@Service
@Slf4j
public class NotificationWebhookService {

    private static final Duration REPLAY_TTL = Duration.ofDays(2);

    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final Map<String, WebhookVerifier> verifiers;

    public NotificationWebhookService(ObjectMapper json, StringRedisTemplate redis, JdbcTemplate jdbc,
                                      List<WebhookVerifier> verifiers) {
        this.json = json;
        this.redis = redis;
        this.jdbc = jdbc;
        this.verifiers = verifiers.stream()
                .collect(Collectors.toMap(v -> v.provider().toLowerCase(), Function.identity()));
    }

    /** Проверяет провайдера, подпись и повтор, применяет событие. */
    public void handle(String provider, String rawBody, Map<String, String> headers) {
        WebhookVerifier verifier = verifiers.get(provider == null ? "" : provider.toLowerCase());
        if (verifier == null) {
            throw new BusinessException("notification.webhook.unsupported_provider", HttpStatus.BAD_REQUEST);
        }
        if (!verifier.verify(rawBody, lowerKeys(headers))) {
            throw new BusinessException("notification.webhook.invalid_signature", HttpStatus.UNAUTHORIZED);
        }
        JsonNode payload = parse(rawBody);
        String dedupKey = replayKey(provider, payload, rawBody);
        if (Boolean.TRUE.equals(redis.hasKey(dedupKey))) {
            log.debug("Duplicate delivery webhook from {}", provider);
            return; // повтор уже применённого события — молча игнорируем (200)
        }
        apply(text(payload, "message_id"), text(payload, "status"), text(payload, "error"));
        // Помечаем обработанным ТОЛЬКО после успешного apply (аудит п.10): сбой БД не «проглотит»
        // событие — честный повтор от провайдера доедет и применится. apply идемпотентен (по
        // provider_message_id), поэтому гонка двух одинаковых доставок безопасна.
        redis.opsForValue().set(dedupKey, "1", REPLAY_TTL);
    }

    /**
     * Ключ дедупликации события. Mailgun — по одноразовому {@code token} (так его подсмотренная тройка
     * не переигрывается с поддельным телом); иначе — по {@code event_id}; иначе, при наличии метки
     * времени, — по хешу тела. Без идентификатора и метки времени тело можно крутить вечно — отклоняем.
     */
    private String replayKey(String provider, JsonNode payload, String rawBody) {
        if ("mailgun".equalsIgnoreCase(provider)) {
            String token = payload.path("signature").path("token").asText(null);
            if (token != null) {
                return "notif:webhook:mg:" + provider + ":" + token;
            }
        }
        String eventId = text(payload, "event_id");
        if (eventId != null) {
            return "notif:webhook:evt:" + provider + ":" + eventId;
        }
        if (timestamp(payload) != null) {
            return "notif:webhook:body:" + provider + ":" + sha256Hex(rawBody);
        }
        throw new BusinessException("notification.webhook.replay_unprotected", HttpStatus.BAD_REQUEST);
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

    /** Метка времени события: верхнеуровневая {@code timestamp} или {@code signature.timestamp} (Mailgun). */
    private static String timestamp(JsonNode node) {
        String top = text(node, "timestamp");
        if (top != null) {
            return top;
        }
        JsonNode sig = node.get("signature");
        return sig == null ? null : text(sig, "timestamp");
    }

    private static Map<String, String> lowerKeys(Map<String, String> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue, (a, b) -> a));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
