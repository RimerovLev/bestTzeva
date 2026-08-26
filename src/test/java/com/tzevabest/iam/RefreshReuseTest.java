package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * R-05: повторное использование ротированного refresh-токена трактуется как кража — все сессии
 * пользователя отзываются, владельцу уходит уведомление.
 */
class RefreshReuseTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private NotificationSender notifications;

    @Test
    void replaying_rotated_refresh_revokes_all_sessions_and_notifies() {
        String email = "reuse-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        TokenResponse session1 = authService.register(
                new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
        TokenResponse session2 = authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1");

        // штатная ротация первой сессии
        TokenResponse rotated1 = authService.refresh(session1.refreshToken(), "ua", "1.1.1.1");

        // имитируем истечение окна снисхождения (R-06): вне него повтор трактуется как кража
        var graceKeys = redis.keys("auth:rotated:*");
        if (graceKeys != null && !graceKeys.isEmpty()) {
            redis.delete(graceKeys);
        }

        // повтор уже ротированного токена вне окна — это replay
        assertThatThrownBy(() -> authService.refresh(session1.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);

        // отозваны ВСЕ сессии: и вторая, и только что выданная при ротации
        assertThatThrownBy(() -> authService.refresh(session2.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> authService.refresh(rotated1.refreshToken(), "ua", "1.1.1.1"))
                .isInstanceOf(BusinessException.class);

        verify(notifications, atLeastOnce()).sendEmail(eq(email), anyString(), anyString());
    }
}
