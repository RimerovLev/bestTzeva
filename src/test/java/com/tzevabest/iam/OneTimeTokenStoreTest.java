package com.tzevabest.iam;

import com.tzevabest.iam.service.OneTimeTokenStore;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Одноразовые токены: однократное использование, повтор и истечение. */
class OneTimeTokenStoreTest extends IntegrationTest {

    @Autowired
    private OneTimeTokenStore store;

    @Test
    void consumed_once_then_reuse_is_rejected() {
        String token = store.issue("test", 7L, Duration.ofHours(1));
        assertThat(store.consume("test", token)).contains(7L);
        assertThat(store.consume("test", token)).isEmpty();
    }

    @Test
    void expired_token_is_not_consumable() throws InterruptedException {
        String token = store.issue("test", 7L, Duration.ofSeconds(1));
        Thread.sleep(1200);
        assertThat(store.consume("test", token)).isEmpty();
    }

    @Test
    void unknown_token_is_empty() {
        assertThat(store.consume("test", "made-up-token")).isEmpty();
    }

    @Test
    void issue_single_invalidates_previous_token() {
        // Аудит п.15: новый токен сброса гасит прежний — одновременно живёт только последняя ссылка.
        String first = store.issueSingle("pwd-reset", 7L, Duration.ofHours(1));
        String second = store.issueSingle("pwd-reset", 7L, Duration.ofHours(1));

        assertThat(store.consume("pwd-reset", first)).isEmpty();   // прежний больше не работает
        assertThat(store.consume("pwd-reset", second)).contains(7L); // действует только последний
    }
}
