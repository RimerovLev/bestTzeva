package com.tzevabest.iam;

import com.tzevabest.iam.service.RefreshTokenStore;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1", TTL.toMillis())).isTrue();
        // повторное использование уже отозванного (ротированного) токена
        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1", TTL.toMillis())).isFalse();
    }

    @Test
    void validate_rejects_wrong_token() {
        store.issue(USER, "t1", "raw-token-1", "agent", "1.1.1.1", TTL);
        assertThat(store.validateAndRotate(USER, "t1", "WRONG", TTL.toMillis())).isFalse();
    }

    @Test
    void revoke_invalidates_token() {
        store.issue(USER, "t1", "raw-token-1", "agent", "1.1.1.1", TTL);
        store.revoke(USER, "t1", TTL);
        assertThat(store.validateAndRotate(USER, "t1", "raw-token-1", TTL.toMillis())).isFalse();
    }

    @Test
    void revoke_all_removes_every_session_and_leaves_no_index_garbage() {
        store.issue(USER, "t1", "r1", "agent", "1.1.1.1", TTL);
        store.issue(USER, "t2", "r2", "agent", "1.1.1.1", TTL);
        store.revokeAllForUser(USER);
        assertThat(keys()).isEmpty();
        // индекс сессий тоже удалён — мусора не остаётся
        assertThat(redis.hasKey(indexKey())).isFalse();
    }

    @Test
    void issued_session_always_has_positive_ttl() {
        store.issue(USER, "t1", "r1", "agent", "1.1.1.1", TTL);
        // hash сессии и индекс получают TTL атомарно с записью — вечных ключей не остаётся
        assertThat(redis.getExpire("auth:refresh:" + USER + ":t1")).isPositive();
        assertThat(redis.getExpire(indexKey())).isPositive();
    }

    @Test
    void parallel_logins_keep_exactly_max_sessions() throws InterruptedException {
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        for (int i = 0; i < attempts; i++) {
            String tid = "t" + i;
            pool.submit(() -> store.issue(USER, tid, "r-" + tid, "agent", "1.1.1.1", TTL));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // атомарный issue не даёт превысить лимит даже при гонке
        assertThat(keys()).hasSize(5);
        assertThat(redis.opsForZSet().zCard(indexKey())).isEqualTo(5L);
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
        assertThat(store.validateAndRotate(USER, "oldest", "r-old", TTL.toMillis())).isFalse();
    }

    @Test
    void new_session_is_never_evicted_and_cannot_survive_revoke_all() {
        // Детерминированно воспроизводим гонку из аудита (п.6): предсеансим 5 сессий с БУДУЩИМ score,
        // тогда новый токен (score = now) оказывается «старейшим» кандидатом на вытеснение. Баг: его
        // удаляли из индекса, но hash пересоздавался → валидная сессия вне индекса, переживающая
        // revokeAllForUser (сброс пароля / logout-all).
        long future = System.currentTimeMillis() + 1_000_000;
        for (int i = 1; i <= 5; i++) {
            redis.opsForZSet().add(indexKey(), "pre" + i, future);
        }
        store.issue(USER, "fresh", "r-fresh", "agent", "1.1.1.1", TTL);

        // новый токен остаётся в индексе (вытеснили кого-то из предсеансенных, но не его)
        assertThat(redis.opsForZSet().score(indexKey(), "fresh")).isNotNull();
        assertThat(redis.opsForZSet().zCard(indexKey())).isEqualTo(5L);

        // и, как следствие, revokeAllForUser реально его убивает — осиротевшей сессии не остаётся
        store.revokeAllForUser(USER);
        assertThat(store.validateAndRotate(USER, "fresh", "r-fresh", TTL.toMillis())).isFalse();
        assertThat(keys()).isEmpty();
    }

    private Set<String> keys() {
        return redis.keys("auth:refresh:" + USER + ":*");
    }

    private static String indexKey() {
        return "auth:refresh:" + USER;
    }
}
