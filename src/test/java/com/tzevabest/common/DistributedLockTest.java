package com.tzevabest.common;

import com.tzevabest.common.lock.DistributedLock;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-56: снятие блокировки — только по владельцу (compare-and-delete). Просроченная и перезахваченная
 * блокировка не снимается опоздавшим владельцем; продление удерживает второй инстанс снаружи.
 */
class DistributedLockTest extends IntegrationTest {

    @Autowired
    private DistributedLock lock;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void late_owner_does_not_delete_key_re_taken_by_another() {
        String key = "test:lock:stale";
        DistributedLock.Handle first = lock.tryAcquire(key, Duration.ofSeconds(1)).orElseThrow();

        // Имитация просрочки TTL: ключ исчез, второй инстанс перезахватил блокировку.
        redis.delete(key);
        DistributedLock.Handle second = lock.tryAcquire(key, Duration.ofSeconds(60)).orElseThrow();

        // Опоздавший первый владелец доходит до finally — НЕ должен снять чужую блокировку.
        first.close();
        assertThat(redis.hasKey(key)).isTrue();

        // Настоящий владелец снимает свою.
        second.close();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void extend_keeps_second_instance_out_until_released() {
        String key = "test:lock:extend";
        DistributedLock.Handle held = lock.tryAcquire(key, Duration.ofSeconds(2)).orElseThrow();

        assertThat(lock.tryAcquire(key, Duration.ofSeconds(2))).isEmpty(); // занято

        assertThat(held.extend()).isTrue();                                 // продлили — всё ещё наша
        assertThat(lock.tryAcquire(key, Duration.ofSeconds(2))).isEmpty();  // второй инстанс не заходит

        held.close();
        Optional<DistributedLock.Handle> after = lock.tryAcquire(key, Duration.ofSeconds(2));
        assertThat(after).isPresent();                                      // после снятия — свободно
        after.get().close();
    }

    @Test
    void extend_fails_when_ownership_lost() {
        String key = "test:lock:lost";
        DistributedLock.Handle held = lock.tryAcquire(key, Duration.ofSeconds(1)).orElseThrow();

        redis.delete(key);                                    // TTL истёк
        lock.tryAcquire(key, Duration.ofSeconds(60));         // ключ перезахвачен другим владельцем

        assertThat(held.extend()).isFalse();                  // продлить чужую нельзя — владение утеряно
    }
}
