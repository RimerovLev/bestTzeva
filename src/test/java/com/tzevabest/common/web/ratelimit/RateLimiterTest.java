package com.tzevabest.common.web.ratelimit;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ограничитель: срабатывание лимита, Retry-After и сброс окна по истечении. */
class RateLimiterTest extends IntegrationTest {

    @Autowired
    private RateLimiter limiter;

    @Test
    void allows_up_to_limit_then_blocks_with_retry_after() {
        String key = "t-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(key, 3, Duration.ofSeconds(60)).allowed()).isTrue();
        }
        RateLimiter.Result blocked = limiter.tryAcquire(key, 3, Duration.ofSeconds(60));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isPositive();
    }

    @Test
    void window_resets_after_expiry() throws InterruptedException {
        String key = "t-" + UUID.randomUUID();
        limiter.tryAcquire(key, 2, Duration.ofSeconds(1));
        limiter.tryAcquire(key, 2, Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire(key, 2, Duration.ofSeconds(1)).allowed()).isFalse();

        Thread.sleep(1100);
        assertThat(limiter.tryAcquire(key, 2, Duration.ofSeconds(1)).allowed()).isTrue();
    }
}
