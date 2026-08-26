package com.tzevabest.iam;

import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Учёт подозрительной активности (через сервис, минуя HTTP rate limit): серия неудач уведомляет
 * владельца, но НЕ блокирует вход (после R-04 блокировки по userId нет — она была оракулом
 * существования аккаунта). Успешный вход сбрасывает счётчик.
 */
class AuthLockoutTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private NotificationSender notifications;

    @Test
    void notifies_owner_after_repeated_failures_but_does_not_lock() {
        String email = register();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"))
                    .isInstanceOf(com.tzevabest.common.web.error.BusinessException.class);
        }
        // владелец уведомлён о подозрительной активности
        verify(notifications, atLeastOnce()).sendEmail(eq(email), anyString(), anyString());

        // но аккаунт НЕ заблокирован — верный пароль по-прежнему пускает
        assertThatCode(() -> authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void successful_login_resets_failure_counter() {
        String email = register();

        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"));
        }
        // успешный вход сбрасывает счётчик
        authService.login(new LoginRequest(email, "Passw0rd"), "ua", "1.1.1.1");

        // ещё 9 неудач — порог (10) не достигнут, владельца не тревожим повторно
        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPass9"), "ua", "1.1.1.1"));
        }
        verify(notifications, never()).sendEmail(anyString(), anyString(), anyString());
    }

    private String register() {
        String email = "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
        return email;
    }
}
