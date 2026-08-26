package com.tzevabest.iam;

import com.tzevabest.common.web.error.TooManyRequestsException;
import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Прогрессивная блокировка (через сервис, минуя HTTP rate limit): 10 неудач → блокировка + уведомление;
 * успешный вход сбрасывает счётчик.
 */
class AuthLockoutTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private NotificationSender notifications;

    @Test
    void locks_account_after_ten_failures_and_notifies() {
        String email = register();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"))
                    .isInstanceOf(com.tzevabest.common.web.error.BusinessException.class);
        }
        // теперь аккаунт заблокирован — даже верный пароль отвергается 429
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(notifications, atLeastOnce()).sendEmail(eq(email), anyString(), anyString());
    }

    @Test
    void successful_login_resets_failure_counter() {
        String email = register();

        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"));
        }
        // успешный вход сбрасывает счётчик
        authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1");

        // ещё 9 неудач — не должно заблокировать (счётчик был сброшен)
        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"));
        }
        assertThatCode(() -> authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1"))
                .doesNotThrowAnyException();
    }

    private String register() {
        String email = "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
        return email;
    }
}
