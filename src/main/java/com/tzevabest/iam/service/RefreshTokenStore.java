package com.tzevabest.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Хранение и ротация refresh-токенов в Redis. Ключ {@code auth:refresh:{userId}:{tokenId}},
 * значение — hash: {@code hash} (SHA-256 токена), {@code ua}, {@code ip}, {@code createdAt}.
 * TTL равен сроку жизни refresh. Одновременных сессий на пользователя — не более
 * {@code app.auth.max-sessions} (по умолчанию 5): при превышении удаляется самая старая.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    /** Атомарно: если сохранённый hash совпал — удалить ключ и вернуть 1, иначе 0. */
    private static final RedisScript<Long> VALIDATE_AND_DELETE = RedisScript.of(
            "if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then "
                    + "redis.call('DEL', KEYS[1]); return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    @Value("${app.auth.max-sessions:5}")
    private int maxSessions;

    /** Сохранить refresh-сессию. Соблюдает лимит одновременных сессий. */
    public void issue(long userId, String tokenId, String rawToken, String userAgent, String ip, Duration ttl) {
        enforceSessionLimit(userId);
        String key = key(userId, tokenId);
        redis.opsForHash().putAll(key, Map.of(
                "hash", sha256(rawToken),
                "ua", userAgent == null ? "" : userAgent,
                "ip", ip == null ? "" : ip,
                "createdAt", Long.toString(Instant.now().toEpochMilli())));
        redis.expire(key, ttl);
    }

    /**
     * Атомарно проверяет токен и удаляет старую запись (ротация). {@code true} — токен был
     * валиден и удалён; повторный вызов с тем же токеном вернёт {@code false} (защита от reuse).
     */
    public boolean validateAndRotate(long userId, String tokenId, String rawToken) {
        Long result = redis.execute(VALIDATE_AND_DELETE, List.of(key(userId, tokenId)), sha256(rawToken));
        return result != null && result == 1L;
    }

    public void revoke(long userId, String tokenId) {
        redis.delete(key(userId, tokenId));
    }

    public void revokeAllForUser(long userId) {
        Set<String> keys = redis.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void enforceSessionLimit(long userId) {
        Set<String> keys = redis.keys(KEY_PREFIX + userId + ":*");
        if (keys == null || keys.size() < maxSessions) {
            return;
        }
        List<String> oldestFirst = keys.stream()
                .sorted(Comparator.comparingLong(this::createdAt))
                .toList();
        int toRemove = keys.size() - maxSessions + 1; // освободить место под новую сессию
        for (int i = 0; i < toRemove && i < oldestFirst.size(); i++) {
            redis.delete(oldestFirst.get(i));
        }
    }

    private long createdAt(String key) {
        Object value = redis.opsForHash().get(key, "createdAt");
        try {
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String key(long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
