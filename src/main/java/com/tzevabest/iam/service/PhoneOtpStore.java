package com.tzevabest.iam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OTP-коды подтверждения телефона в Redis. Ключ {@code auth:otp:{userId}} — hash с полями
 * {@code hash} (SHA-256 кода) и {@code attempts}. Проверка атомарна (Lua): инкремент попытки,
 * блокировка при превышении лимита, сверка кода. Отдельный ключ cooldown ограничивает частоту.
 */
@Component
@RequiredArgsConstructor
public class PhoneOtpStore {

    private static final String OTP_PREFIX = "auth:otp:";
    private static final String COOLDOWN_PREFIX = "auth:otp-cooldown:";

    /**
     * @return 1 — верно (ключ удаляется); 0 — неверно; -1 — превышен лимит попыток; -2 — код
     * отсутствует/истёк. При -1 ключ НЕ удаляется (блокировка идемпотентна и держится до TTL или
     * нового кода) — повторный запрос стабильно получает 429, не «протекая» в -2.
     */
    private static final RedisScript<Long> VERIFY = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -2 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts > tonumber(ARGV[2]) then return -1 end
            if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public boolean canSend(long userId) {
        return !Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + userId));
    }

    public void issue(long userId, String code, Duration ttl, Duration cooldown) {
        String key = OTP_PREFIX + userId;
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of("hash", OneTimeTokenStore.sha256(code), "attempts", "0"));
        redis.expire(key, ttl);
        redis.opsForValue().set(COOLDOWN_PREFIX + userId, "1", cooldown);
    }

    public long verify(long userId, String code, int maxAttempts) {
        Long result = redis.execute(VERIFY, List.of(OTP_PREFIX + userId),
                OneTimeTokenStore.sha256(code), Integer.toString(maxAttempts));
        return result == null ? -2 : result;
    }
}
