package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * R-37: FAILURE-лимит по логину тратит бюджет только на клиентских ошибках (BusinessException), а не
 * на инфраструктурных сбоях — иначе кратковременная недоступность БД/Redis списала бы попытки, и
 * пользователь получил бы 429 после восстановления сервиса.
 */
class LoginFailureBudgetTest extends IntegrationTest {

    @MockBean
    private AuthService authService;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void infrastructure_failure_does_not_consume_login_budget() {
        when(authService.login(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        String email = "budget-infra@x.test";
        postLogin(email);

        // бюджет FAILURE-лимита по этому логину не тронут
        assertThat(budget(email)).isZero();
    }

    @Test
    void client_error_consumes_login_budget() {
        when(authService.login(any(), any(), any()))
                .thenThrow(new BusinessException("common.auth.invalid_credentials", HttpStatus.UNAUTHORIZED));

        String email = "budget-client@x.test";
        postLogin(email);

        // неверные учётные данные — попытка списана
        assertThat(budget(email)).isEqualTo(1L);
    }

    private void postLogin(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"login\":\"" + email + "\",\"password\":\"whatever9\"}";
        rest.exchange("/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private long budget(String email) {
        Long card = redis.opsForZSet().zCard("rl:AuthController.login:field:login:" + email);
        return card == null ? 0L : card;
    }
}
