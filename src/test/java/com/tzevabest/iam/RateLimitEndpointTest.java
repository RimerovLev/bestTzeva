package com.tzevabest.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лимит login по IP: 10 попыток за 5 минут, 11-я — 429 + Retry-After + problem+json.
 * Используется RestTemplate с ОТКЛЮЧЁННЫМИ авто-ретраями — иначе HttpClient5 при 429 c большим
 * Retry-After спал бы всё окно.
 */
class RateLimitEndpointTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private RestTemplate rest;

    @BeforeEach
    void initClient() {
        var factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableAutomaticRetries().build());
        rest = new RestTemplate(factory);
        rest.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // не бросать исключения на 4xx — читаем статус сами
            }
        });
    }

    @Test
    void login_ip_limit_returns_429_with_retry_after() {
        // Разные email → срабатывает именно IP-лимит (10), а не per-email (5).
        for (int i = 0; i < 10; i++) {
            assertThat(login("u" + i + "-" + token() + "@x.test").getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<String> eleventh = login("final-" + token() + "@x.test");
        assertThat(eleventh.getStatusCode().value()).isEqualTo(429);
        assertThat(eleventh.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(eleventh.getBody()).contains("common.rate_limited");
    }

    private ResponseEntity<String> login(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = json.createObjectNode().put("login", email).put("password", "whatever9").toString();
        return rest.exchange("http://localhost:" + port + "/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
