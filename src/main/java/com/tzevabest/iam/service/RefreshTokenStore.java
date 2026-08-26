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
import java.util.HexFormat;
import java.util.List;

/**
 * Хранение и ротация refresh-токенов в Redis.
 * <ul>
 *   <li>сессия — hash {@code auth:refresh:{userId}:{tokenId}}: {@code hash} (SHA-256 токена),
 *       {@code ua}, {@code ip}, {@code createdAt}; TTL равен сроку жизни refresh;</li>
 *   <li>индекс сессий пользователя — sorted set {@code auth:refresh:{userId}}: member = tokenId,
 *       score = createdAt. По нему считается лимит и делается массовый отзыв, поэтому команда
 *       {@code KEYS} (обход всего пространства ключей на каждом входе) больше не нужна.</li>
 * </ul>
 * Одновременных сессий на пользователя — не более {@code app.auth.max-sessions} (по умолчанию 5):
 * при превышении вытесняется самая старая. Проверка лимита, запись и установка TTL атомарны (Lua).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    /**
     * Атомарно: почистить индекс от протухших сессий, добавить новую, обрезать до лимита (вытесняя
     * старейшие вместе с их hash), записать hash сессии и проставить TTL и на hash, и на индекс.
     * KEYS[1]=индекс. ARGV: now, ttlMs, maxSessions, tokenId, sessionPrefix, hash, ua, ip.
     */
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            local index = KEYS[1]
            local now = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local maxSessions = tonumber(ARGV[3])
            local tokenId = ARGV[4]
            local prefix = ARGV[5]
            redis.call('ZREMRANGEBYSCORE', index, 0, now - ttl)
            redis.call('ZADD', index, now, tokenId)
            local count = redis.call('ZCARD', index)
            if count > maxSessions then
                local old = redis.call('ZRANGE', index, 0, count - maxSessions - 1)
                for i = 1, #old do
                    redis.call('DEL', prefix .. old[i])
                    redis.call('ZREM', index, old[i])
                end
            end
            local key = prefix .. tokenId
            redis.call('HSET', key, 'hash', ARGV[6], 'ua', ARGV[7], 'ip', ARGV[8], 'createdAt', now)
            redis.call('PEXPIRE', key, ttl)
            redis.call('PEXPIRE', index, ttl)
            return 1
            """, Long.class);

    /**
     * Атомарно: если сохранённый hash совпал — удалить hash сессии и её член индекса, вернуть 1;
     * иначе 0. Повторный вызов с тем же токеном вернёт 0 (защита от reuse).
     * KEYS[1]=hash сессии, KEYS[2]=индекс. ARGV[1]=hash, ARGV[2]=tokenId.
     */
    private static final RedisScript<Long> VALIDATE_AND_DELETE = RedisScript.of("""
            if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('ZREM', KEYS[2], ARGV[2])
                return 1
            else
                return 0
            end
            """, Long.class);

    /**
     * Массовый отзыв: удалить hash всех сессий из индекса и сам индекс. KEYS[1]=индекс,
     * ARGV[1]=sessionPrefix. Возвращает число удалённых сессий.
     */
    private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for i = 1, #members do
                redis.call('DEL', ARGV[1] .. members[i])
            end
            redis.call('DEL', KEYS[1])
            return #members
            """, Long.class);

    private final StringRedisTemplate redis;

    @Value("${app.auth.max-sessions:5}")
    private int maxSessions;

    /** Сохранить refresh-сессию. Атомарно соблюдает лимит одновременных сессий и проставляет TTL. */
    public void issue(long userId, String tokenId, String rawToken, String userAgent, String ip, Duration ttl) {
        redis.execute(ISSUE, List.of(indexKey(userId)),
                Long.toString(Instant.now().toEpochMilli()),
                Long.toString(ttl.toMillis()),
                Integer.toString(maxSessions),
                tokenId,
                sessionPrefix(userId),
                sha256(rawToken),
                userAgent == null ? "" : userAgent,
                ip == null ? "" : ip);
    }

    /**
     * Атомарно проверяет токен и удаляет старую запись (ротация). {@code true} — токен был
     * валиден и удалён; повторный вызов с тем же токеном вернёт {@code false} (защита от reuse).
     */
    public boolean validateAndRotate(long userId, String tokenId, String rawToken) {
        Long result = redis.execute(VALIDATE_AND_DELETE,
                List.of(sessionKey(userId, tokenId), indexKey(userId)), sha256(rawToken), tokenId);
        return result != null && result == 1L;
    }

    public void revoke(long userId, String tokenId) {
        redis.delete(sessionKey(userId, tokenId));
        redis.opsForZSet().remove(indexKey(userId), tokenId);
    }

    public void revokeAllForUser(long userId) {
        redis.execute(REVOKE_ALL, List.of(indexKey(userId)), sessionPrefix(userId));
    }

    private static String indexKey(long userId) {
        return KEY_PREFIX + userId;
    }

    private static String sessionPrefix(long userId) {
        return KEY_PREFIX + userId + ":";
    }

    private static String sessionKey(long userId, String tokenId) {
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
