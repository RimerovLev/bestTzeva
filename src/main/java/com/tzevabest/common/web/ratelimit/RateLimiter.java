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

    /** ARGV: now(ms), window(ms), limit. Только проверка: чистит окно, считает, НЕ добавляет. */
    private static final RedisScript<List> CHECK_SCRIPT = RedisScript.of("""
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
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redis;

    public record Result(boolean allowed, long retryAfterSeconds) {
    }

    /** Проверить и, если бюджет есть, занять слот (атомарно). Обычный лимит частоты. */
    public Result tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID();
        List<Long> result = execute(SCRIPT, key,
                Long.toString(now), Long.toString(window.toMillis()), Integer.toString(limit), member);
        return toResult(result);
    }

    /**
     * Проверить бюджет БЕЗ записи слота. Пара к {@link #record}: используется для {@link CountOn#FAILURE},
     * где слот занимает только неудачный вызов. Проверка и запись не атомарны — для throttle это допустимо
     * (окно само рассасывается), это не жёсткая блокировка.
     */
    public Result check(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        List<Long> result = execute(CHECK_SCRIPT, key,
                Long.toString(now), Long.toString(window.toMillis()), Integer.toString(limit));
        return toResult(result);
    }

    /** Занять слот в окне без проверки лимита. Пара к {@link #check}. */
    public void record(String key, Duration window) {
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID();
        execute(RECORD_SCRIPT, key,
                Long.toString(now), Long.toString(window.toMillis()), member);
    }

    @SuppressWarnings("unchecked")
    private List<Long> execute(RedisScript<List> script, String key, String... args) {
        return redis.execute(script, List.of("rl:" + key), (Object[]) args);
    }

    private static Result toResult(List<Long> result) {
        boolean allowed = result != null && result.get(0) == 1L;
        long retryMs = (result == null) ? 0 : result.get(1);
        long retrySeconds = Math.max(1, (retryMs + 999) / 1000);
        return new Result(allowed, allowed ? 0 : retrySeconds);
    }

    /** ARGV: now(ms), window(ms), member. Безусловно добавляет слот и продлевает TTL окна. */
    private static final RedisScript<List> RECORD_SCRIPT = RedisScript.of("""
            redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[3])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
            return {1, 0}
            """, List.class);
}
