package com.tzevabest.iam;

import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.iam.config.JwtProperties;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.iam.web.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-08: за доверенным прокси CurrentUser.ip() возвращает реальный адрес клиента из X-Forwarded-For,
 * а не адрес балансировщика (getRemoteAddr). IP разрешает ClientIpResolver внутри JwtAuthenticationFilter.
 */
class JwtFilterClientIpTest {

    private final JwtService jwt = new JwtService(new JwtProperties(
            "test-secret-that-is-at-least-32-bytes-long!!", "tzevabest",
            Duration.ofMinutes(15), Duration.ofDays(30)));
    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolves_client_ip_from_forwarded_for_behind_trusted_proxy() throws Exception {
        // прокси 10.0.0.0/8 доверенный
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, resolver);

        String token = jwt.generateAccess(7L, "CUSTOMER", "u@x.test", "he");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");                       // адрес балансировщика
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Forwarded-For", "203.0.113.7");     // реальный клиент
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(currentUser.ip()).isEqualTo("203.0.113.7");
    }

    @Test
    void falls_back_to_remote_addr_for_untrusted_sender() throws Exception {
        // никаких доверенных прокси — XFF игнорируется, берём getRemoteAddr
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, resolver);

        String token = jwt.generateAccess(7L, "CUSTOMER", "u@x.test", "he");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Forwarded-For", "203.0.113.7"); // не доверяем — подделка игнорируется
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(currentUser.ip()).isEqualTo("198.51.100.9");
    }
}
