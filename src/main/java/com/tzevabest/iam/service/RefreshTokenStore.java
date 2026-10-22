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
 *       score = createdAt; по нему считается лимит и делается массовый отзыв (без {@code KEYS});</li>
 *   <li>маркер выбывшей сессии — {@code auth:retired:{userId}:{tokenId}} = {@code reason|graceUntil|payload},
 *       живёт до конца жизни токена. Причина объясняет, ПОЧЕМУ сессия исчезла, и позволяет отличить
 *       кражу (повтор {@code rotated} за окном снисхождения) от законного исчезновения
 *       ({@code evicted}/{@code logged_out}) — см. R-34. Для {@code rotated} в {@code payload} лежит
 *       зашифрованная выданная пара (окно снисхождения R-06), либо {@code PENDING}, пока победившая
 *       ротация её не допишет.</li>
 * </ul>
 * Одновременных сессий на пользователя — не более {@code app.auth.max-sessions} (по умолчанию 5).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String RETIRED_PREFIX = "auth:retired:";

    /** Окно снисхождения для гонки параллельного refresh (R-06). */
    static final Duration GRACE = Duration.ofSeconds(30);

    static final String REASON_ROTATED = "rotated";
    static final String REASON_LOGGED_OUT = "logged_out";
    static final String REASON_EVICTED = "evicted";
    private static final String PENDING = "PENDING";
    private static final char SEP = '|';

    // Ожидание, пока победившая ротация допишет пару вместо PENDING: ротация — это пара обращений к
    // Redis и подпись токена; не уложилась за ~150 мс — значит не завершится (R-36).
    private static final int WAIT_ATTEMPTS = 6;
    private static final long WAIT_STEP_MS = 25;

    /**
     * ISSUE: почистить индекс от протухших, добавить новую сессию, обрезать до лимита (вытесняя
     * старейшие вместе с hash и пометив их {@code evicted}), записать hash и TTL на hash и индекс.
     * KEYS[1]=индекс. ARGV: now, ttlMs, maxSessions, tokenId, sessionPrefix, hash, ua, ip, retiredPrefix.
     */
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            local index = KEYS[1]
            local now = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local maxSessions = tonumber(ARGV[3])
            local tokenId = ARGV[4]
            local prefix = ARGV[5]
            local retiredPrefix = ARGV[9]
            redis.call('ZREMRANGEBYSCORE', index, 0, now - ttl)
            redis.call('ZADD', index, now, tokenId)
            local count = redis.call('ZCARD', index)
            if count > maxSessions then
                -- Вытесняем старейшие, но НИКОГДА — только что добавленный токен. При равных score
                -- (несколько логинов в одну мс) ZRANGE рвёт ничью лексикографически по tokenId и мог бы
                -- выбрать новый токен: его hash ниже всё равно пересоздаётся HSET'ом, и осталась бы
                -- валидная сессия вне индекса — переживающая REVOKE_ALL (сброс пароля / logout-all). R-?? / аудит п.6.
                local toEvict = count - maxSessions
                local candidates = redis.call('ZRANGE', index, 0, -1)
                local evicted = 0
                for i = 1, #candidates do
                    if evicted >= toEvict then break end
                    if candidates[i] ~= tokenId then
                        redis.call('DEL', prefix .. candidates[i])
                        redis.call('ZREM', index, candidates[i])
                        redis.call('SET', retiredPrefix .. candidates[i], 'evicted|0|', 'PX', ttl)
                        evicted = evicted + 1
                    end
                end
            end
            local key = prefix .. tokenId
            redis.call('HSET', key, 'hash', ARGV[6], 'ua', ARGV[7], 'ip', ARGV[8], 'createdAt', now)
            redis.call('PEXPIRE', key, ttl)
            redis.call('PEXPIRE', index, ttl)
            return 1
            """, Long.class);

    /**
     * VALIDATE_AND_DELETE: если hash совпал — удалить hash сессии и её член индекса, пометить
     * {@code rotated} с PENDING-пейлоадом на остаток жизни токена и вернуть 1; иначе 0.
     * KEYS[1]=hash сессии, KEYS[2]=индекс, KEYS[3]=retired-ключ.
     * ARGV[1]=hash, ARGV[2]=tokenId, ARGV[3]=tokenRemainderMs, ARGV[4]=graceUntilMs.
     */
    private static final RedisScript<Long> VALIDATE_AND_DELETE = RedisScript.of("""
            if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('ZREM', KEYS[2], ARGV[2])
                redis.call('SET', KEYS[3], 'rotated|' .. ARGV[4] .. '|PENDING', 'PX', tonumber(ARGV[3]))
                return 1
            else
                return 0
            end
            """, Long.class);

    /** Массовый отзыв: удалить hash всех сессий из индекса и сам индекс. */
    private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            for i = 1, #members do
                redis.call('DEL', ARGV[1] .. members[i])
            end
            redis.call('DEL', KEYS[1])
            return #members
            """, Long.class);

    private final StringRedisTemplate redis;
    private final TokenCipher cipher;

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
                ip == null ? "" : ip,
                retiredPrefix(userId));
    }

    /**
     * Атомарно проверяет токен и удаляет запись (ротация). {@code true} — токен был валиден и удалён,
     * сессия помечена {@code rotated}; повторный вызов вернёт {@code false}.
     * {@code tokenRemainderMs} — остаток жизни токена (из claim exp): столько живёт retired-маркер.
     */
    public boolean validateAndRotate(long userId, String tokenId, String rawToken, long tokenRemainderMs) {
        long graceUntil = System.currentTimeMillis() + GRACE.toMillis();
        Long result = redis.execute(VALIDATE_AND_DELETE,
                List.of(sessionKey(userId, tokenId), indexKey(userId), retiredKey(userId, tokenId)),
                sha256(rawToken), tokenId, Long.toString(Math.max(1, tokenRemainderMs)), Long.toString(graceUntil));
        return result != null && result == 1L;
    }

    /**
     * Записать выданную при ротации пару (зашифрованной, R-35) под маркер {@code rotated} на остаток
     * жизни токена — чтобы параллельный повтор внутри окна получил ту же пару (R-06).
     */
    public void rememberRotation(long userId, String oldTokenId,
                                 String access, String refresh, long expiresIn, long tokenRemainderMs) {
        long graceUntil = System.currentTimeMillis() + GRACE.toMillis();
        String encrypted = cipher.encrypt(access + ' ' + refresh + ' ' + expiresIn);
        String value = REASON_ROTATED + SEP + graceUntil + SEP + encrypted;
        redis.opsForValue().set(retiredKey(userId, oldTokenId), value, Duration.ofMillis(Math.max(1, tokenRemainderMs)));
    }

    /**
     * Классифицировать повтор токена, которого нет в хранилище, по retired-маркеру (R-34):
     * <ul>
     *   <li>{@code rotated} внутри окна с готовой парой — {@link ReplayKind#PAIR} (гонка клиента);</li>
     *   <li>{@code rotated} внутри окна, но пара застряла в PENDING — {@link ReplayKind#IN_PROGRESS}
     *       (409, не отзыв, R-36);</li>
     *   <li>{@code rotated} за окном снисхождения — {@link ReplayKind#THEFT} (повтор отработавшего
     *       токена, R-05);</li>
     *   <li>{@code evicted}/{@code logged_out} или маркера нет — {@link ReplayKind#BENIGN} (законное
     *       исчезновение или «не знаем»: обычный 401 без побочных эффектов).</li>
     * </ul>
     */
    public ReplayResult classifyReplay(long userId, String oldTokenId, long nowMs) {
        String key = retiredKey(userId, oldTokenId);
        for (int attempt = 0; attempt < WAIT_ATTEMPTS; attempt++) {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return new ReplayResult(ReplayKind.BENIGN, null); // «не знаем» — не кража
            }
            String[] parts = value.split("\\" + SEP, 3);
            String reason = parts[0];
            if (!REASON_ROTATED.equals(reason)) {
                return new ReplayResult(ReplayKind.BENIGN, null); // evicted / logged_out
            }
            long graceUntil = Long.parseLong(parts[1]);
            String payload = parts.length > 2 ? parts[2] : "";
            if (nowMs >= graceUntil) {
                return new ReplayResult(ReplayKind.THEFT, null); // повтор ротированного за окном
            }
            if (PENDING.equals(payload)) {
                sleep(WAIT_STEP_MS); // ждём, пока победившая ротация допишет пару
                continue;
            }
            String[] pair = cipher.decrypt(payload).split(" ");
            return new ReplayResult(ReplayKind.PAIR, new Rotation(pair[0], pair[1], Long.parseLong(pair[2])));
        }
        return new ReplayResult(ReplayKind.IN_PROGRESS, null); // застряло в PENDING — 409, не отзыв
    }

    public enum ReplayKind {PAIR, IN_PROGRESS, THEFT, BENIGN}

    public record ReplayResult(ReplayKind kind, Rotation pair) {
    }

    /** Пара токенов, выданная при ротации (для окна снисхождения R-06). */
    public record Rotation(String accessToken, String refreshToken, long expiresIn) {
    }

    /** Отзыв одной сессии (logout): удалить hash и член индекса, пометить {@code logged_out}. */
    public void revoke(long userId, String tokenId, Duration retiredTtl) {
        redis.delete(sessionKey(userId, tokenId));
        redis.opsForZSet().remove(indexKey(userId), tokenId);
        redis.opsForValue().set(retiredKey(userId, tokenId), REASON_LOGGED_OUT + SEP + '0' + SEP, retiredTtl);
    }

    public void revokeAllForUser(long userId) {
        redis.execute(REVOKE_ALL, List.of(indexKey(userId)), sessionPrefix(userId));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String indexKey(long userId) {
        return KEY_PREFIX + userId;
    }

    private static String retiredKey(long userId, String tokenId) {
        return RETIRED_PREFIX + userId + ":" + tokenId;
    }

    private static String retiredPrefix(long userId) {
        return RETIRED_PREFIX + userId + ":";
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
