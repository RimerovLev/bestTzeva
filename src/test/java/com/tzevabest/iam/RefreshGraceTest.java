package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.dto.TokenResponse;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-06: окно снисхождения для гонки параллельного refresh. Два одновременных запроса с одним токеном
 * получают одну и ту же новую пару (обе успешны), а не разлогин. Вне окна тот же токен — 401 + отзыв.
 */
class RefreshGraceTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private NotificationSender notifications;

    @Test
    void concurrent_refresh_with_same_token_returns_same_pair() throws Exception {
        TokenResponse session = register();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<TokenResponse> call = () -> authService.refresh(session.refreshToken(), "ua", "1.1.1.1");
        List<Future<TokenResponse>> results = pool.invokeAll(List.of(call, call));
        pool.shutdown();

        TokenResponse a = results.get(0).get();
        TokenResponse b = results.get(1).get();
        // обе успешны и вернули одну и ту же новую пару — второй запрос попал в окно снисхождения
        assertThat(a.refreshToken()).isEqualTo(b.refreshToken());
        assertThat(a.accessToken()).isEqualTo(b.accessToken());
        assertThat(a.refreshToken()).isNotEqualTo(session.refreshToken());
    }

    @Test
    void same_token_after_window_is_rejected() {
        TokenResponse session = register();
        authService.refresh(session.refreshToken(), "ua", "1.1.1.1"); // штатная ротация

        // окно снисхождения истекло
        List<String> graceKeys = List.copyOf(redis.keys("auth:rotated:*"));
        if (!graceKeys.isEmpty()) {
            redis.delete(graceKeys);
        }

        assertThatThrownBy(() -> authService.refresh(session.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);
    }

    private TokenResponse register() {
        String email = "grace-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        return authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
    }
}
