package com.tzevabest.iam;

import com.tzevabest.iam.service.RefreshTokenStore;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Хранение/ротация refresh-токенов в Redis (Testcontainers через IntegrationTest). */
class RefreshTokenStoreTest extends IntegrationTest {

    private static final long USER = 42L;
    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    private RefreshTokenStore store;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void validate_and_rotate_accepts_once_then_rejects_reuse() {
        store.issue(USER, "t1", "raw-token-1", "agent", "1.1.1.1", TTL);

        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1")).isTrue();
        // повторное использование уже отозванного (ротированного) токена
        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1")).isFalse();
    }

    @Test
    void validate_rejects_wrong_token() {
        store.issue(USER, "t1", "raw-token-1", "agent", "1.1.1.1", TTL);
        assertThat(store.validateAndRotate(USER, "t1", "WRONG")).isFalse();
    }

    @Test
    void revoke_invalidates_token() {
        store.issue(USER, "t1", "raw-token-1", "agent", "1.1.1.1", TTL);
        store.revoke(USER, "t1");
        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1")).isFalse();
    }

    @Test
    void revoke_all_removes_every_session() {
        store.issue(USER, "t1", "r1", "agent", "1.1.1.1", TTL);
        store.issue(USER, "t2", "r2", "agent", "1.1.1.1", TTL);
        store.revokeAllForUser(USER);
        assertThat(keys()).isEmpty();
    }

    @Test
    void enforces_max_five_sessions_evicting_oldest() throws InterruptedException {
        store.issue(USER, "oldest", "r-old", "agent", "1.1.1.1", TTL);
        Thread.sleep(5); // гарантируем более ранний createdAt
        for (int i = 1; i <= 5; i++) {
            store.issue(USER, "t" + i, "r" + i, "agent", "1.1.1.1", TTL);
        }

        assertThat(keys()).hasSize(5);
        // самая старая сессия вытеснена
        assertThat(store.validateAndRotate(USER, "oldest", "r-old")).isFalse();
    }

    private Set<String> keys() {
        return redis.keys("auth:refresh:" + USER + ":*");
    }
}
