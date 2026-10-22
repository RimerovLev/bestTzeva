package com.tzevabest.common;

import com.tzevabest.common.config.ProdSecretsGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prod-профиль не стартует с дефолтными/слабыми секретами из репозитория. */
class ProdSecretsGuardTest {

    private static final String STRONG = "a-strong-production-secret-value-0123456789";

    private ProdSecretsGuard guardWith(String jwt, String webhook, String unsub, String spam, String pseudo) {
        ProdSecretsGuard g = new ProdSecretsGuard();
        ReflectionTestUtils.setField(g, "jwtSecret", jwt);
        ReflectionTestUtils.setField(g, "webhookSecret", webhook);
        ReflectionTestUtils.setField(g, "unsubscribeSecret", unsub);
        ReflectionTestUtils.setField(g, "spamSecret", spam);
        ReflectionTestUtils.setField(g, "pseudonymSecret", pseudo);
        return g;
    }

    @Test
    void strong_secrets_pass() {
        ProdSecretsGuard g = guardWith(STRONG, STRONG, STRONG, STRONG, STRONG);
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(g, "verify")).doesNotThrowAnyException();
    }

    @Test
    void dev_default_secret_is_rejected() {
        ProdSecretsGuard g = guardWith(STRONG, STRONG, STRONG,
                "dev-only-spam-form-token-secret-change-me", STRONG);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(g, "verify"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void too_short_secret_is_rejected() {
        ProdSecretsGuard g = guardWith(STRONG, STRONG, STRONG, STRONG, "short");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(g, "verify"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blank_secret_is_rejected() {
        ProdSecretsGuard g = guardWith(STRONG, STRONG, STRONG, STRONG, "  ");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(g, "verify"))
                .isInstanceOf(IllegalStateException.class);
    }
}
