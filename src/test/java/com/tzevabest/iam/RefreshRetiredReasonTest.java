package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.RefreshInProgressException;
import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.dto.TokenResponse;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R-34: обнаружение кражи не должно срабатывать на законном исчезновении сессии (вытеснение по
 * лимиту, logout). R-36: застрявший PENDING даёт 409, а не массовый отзыв.
 */
class RefreshRetiredReasonTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private NotificationSender notifications;

    @Test
    void refresh_from_evicted_session_is_benign_and_keeps_others() {
        String email = register();
        // 6 сессий при max-sessions=5: старейшая (первая) вытесняется
        List<TokenResponse> sessions = new ArrayList<>();
        sessions.add(login(email));
        for (int i = 0; i < 5; i++) {
            sessions.add(login(email));
        }
        TokenResponse evicted = sessions.get(0);
        TokenResponse newest = sessions.get(sessions.size() - 1);

        // обновление с вытесненного устройства — обычный 401, без массового отзыва и письма
        assertThatThrownBy(() -> authService.refresh(evicted.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);
        verify(notifications, never()).sendEmail(anyString(), anyString(), anyString());
        // остальные сессии живы
        assertThatCode(() -> authService.refresh(newest.refreshToken(), "ua", "1.1.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void refresh_after_logout_of_same_token_is_benign_and_keeps_others() {
        String email = register();
        TokenResponse a = login(email);
        TokenResponse b = login(email);

        authService.logout(userIdOf(email), a.refreshToken());

        // гонка выхода: refresh отозванным токеном — обычный 401, без отзыва второй сессии и письма
        assertThatThrownBy(() -> authService.refresh(a.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);
        verify(notifications, never()).sendEmail(anyString(), anyString(), anyString());
        assertThatCode(() -> authService.refresh(b.refreshToken(), "ua", "1.1.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void stuck_pending_returns_409_without_revoking() {
        String email = register();
        TokenResponse session = login(email);
        authService.refresh(session.refreshToken(), "ua", "1.1.1.1"); // ротация → retired-маркер

        // симулируем застрявший PENDING внутри окна: победившая ротация «оборвалась»
        long graceUntil = System.currentTimeMillis() + 60_000;
        redis.keys("auth:retired:*").forEach(key -> redis.opsForValue().set(key, "rotated|" + graceUntil + "|PENDING"));

        long start = System.nanoTime();
        assertThatThrownBy(() -> authService.refresh(session.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(RefreshInProgressException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Не ждём истечения grace-окна (60с): возвращаем 409 сразу. Порог с запасом под нагрузку CI
        // (важно, что это доли секунды, а не десятки секунд ожидания).
        assertThat(elapsedMs).isLessThan(2_000);         // R-36
        verify(notifications, never()).sendEmail(anyString(), anyString(), anyString());
    }

    private String register() {
        String email = "ret-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
        return email;
    }

    private TokenResponse login(String email) {
        return authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1");
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject(
                "select id from users where lower(email) = lower(?)", Long.class, email);
    }
}
