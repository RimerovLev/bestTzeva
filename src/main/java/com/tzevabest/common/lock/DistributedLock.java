package com.tzevabest.common.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Распределённая блокировка на Redis с проверкой владельца при снятии (R-56).
 * <p>
 * Ключ берётся {@code SET key token NX PX ttl} с уникальным токеном владельца, а снимается только
 * скриптом compare-and-delete: если под ключом лежит НАШ токен — удаляем, иначе не трогаем. Это
 * закрывает гонку, в которой проход не уложился в TTL, ключ истёк, второй инстанс перезахватил
 * блокировку, а первый в {@code finally} удалил бы чужую (безусловный {@code DEL}). Продление
 * ({@link Handle#extend()}) тоже под проверкой владельца — истёкшую и перезахваченную блокировку
 * продлить нельзя.
 * <p>
 * Единая точка вместо копии приёма в каждом планировщике: dispatcher уведомлений, уборка резервов,
 * ретеншен аудита, уборка фото.
 */
@Component
public class DistributedLock {

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final RedisScript<Long> EXTEND = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Пытается взять блокировку. Пустой результат — уже держит кто-то другой. */
    public Optional<Handle> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(new Handle(key, token, ttl)) : Optional.empty();
    }

    /**
     * Выполняет действие под блокировкой, если удалось её взять; иначе возвращает {@code ifBusy}.
     * Блокировка гарантированно снимается (compare-and-delete) даже при исключении.
     */
    public <T> T runIfAcquired(String key, Duration ttl, Function<Handle, T> action, T ifBusy) {
        Optional<Handle> handle = tryAcquire(key, ttl);
        if (handle.isEmpty()) {
            return ifBusy;
        }
        try (Handle h = handle.get()) {
            return action.apply(h);
        }
    }

    /** Владение блокировкой: продление на ходу и безопасное снятие по токену. */
    public final class Handle implements AutoCloseable {

        private final String key;
        private final String token;
        private final long ttlMillis;
        private boolean released;

        private Handle(String key, String token, Duration ttl) {
            this.key = key;
            this.token = token;
            this.ttlMillis = ttl.toMillis();
        }

        /**
         * Продлевает блокировку на исходный TTL, если она всё ещё наша. Возвращает {@code false},
         * если владение утеряно (TTL истёк и ключ перезахвачен) — это сигнал прекратить работу.
         */
        public boolean extend() {
            Long r = redis.execute(EXTEND, List.of(key), token, String.valueOf(ttlMillis));
            return r != null && r == 1L;
        }

        /** Снимает блокировку только если под ключом всё ещё наш токен (чужую не трогаем). */
        @Override
        public void close() {
            if (released) {
                return;
            }
            released = true;
            redis.execute(RELEASE, List.of(key), token);
        }
    }
}
