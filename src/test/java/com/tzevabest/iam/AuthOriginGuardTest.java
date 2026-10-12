package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.iam.web.AuthOriginGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Контракт CSRF-защиты по Origin (B-01.5): чужой Origin — 403 с кодом, свой и отсутствующий — проходят.
 * Юнит-уровень, потому что в интеграции кросс-доменный запрос первым перехватывает CORS-фильтр.
 */
class AuthOriginGuardTest {

    private final AuthOriginGuard guard =
            new AuthOriginGuard(List.of("http://localhost:3000", "http://localhost:5173"));

    @Test
    void foreign_origin_is_forbidden() {
        assertThatThrownBy(() -> guard.check(withOrigin("https://evil.example")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("common.auth.origin_not_allowed");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void allowed_origin_passes() {
        assertThatCode(() -> guard.check(withOrigin("http://localhost:3000"))).doesNotThrowAnyException();
    }

    @Test
    void absent_origin_passes() {
        // не-браузерные клиенты (мобильное приложение) Origin не ставят — им кука недоступна
        assertThatCode(() -> guard.check(withOrigin(null))).doesNotThrowAnyException();
    }

    private static HttpServletRequest withOrigin(String origin) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn(origin);
        return request;
    }
}
