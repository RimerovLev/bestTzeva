package com.tzevabest.common.web.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Ограничитель частоты поверх Redis (sliding window log в sorted set). Проверка и добавление
 * атомарны (Lua): удаляем протухшие записи, считаем текущие, при переполнении возвращаем время
 * до освобождения окна, иначе добавляем запись.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    /** ARGV: now(ms), window(ms), limit, member. Возвращает {allowed(1/0), retryAfter(ms)}. */
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local retry = window - (now - tonumber(oldest[2]))
                if retry < 0 then retry = 0 end
                return {0, retry}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redis;

    public record Result(boolean allowed, long retryAfterSeconds) {
    }

    @SuppressWarnings("unchecked")
    public Result tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID();
        List<Long> result = redis.execute(SCRIPT, List.of("rl:" + key),
                Long.toString(now), Long.toString(window.toMillis()), Integer.toString(limit), member);
        boolean allowed = result != null && result.get(0) == 1L;
        long retryMs = (result == null) ? 0 : result.get(1);
        long retrySeconds = Math.max(1, (retryMs + 999) / 1000);
        return new Result(allowed, allowed ? 0 : retrySeconds);
    }
}
