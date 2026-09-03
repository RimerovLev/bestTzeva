package com.tzevabest.notification;

import com.tzevabest.notification.service.UnsubscribeService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-62: просроченный токен отписки отклоняется. Срок задан отрицательным (истёк в момент выдачи),
 * чтобы проверить окончание срока без ожидания.
 */
@TestPropertySource(properties = "app.notification.unsubscribe-ttl=PT-1S")
class UnsubscribeTokenExpiryTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UnsubscribeService unsubscribe;

    @Test
    void expired_token_is_rejected_on_both_get_and_post() {
        String link = unsubscribe.unsubscribeLink("late@example.com");
        String token = link.substring(link.indexOf("token=") + "token=".length());

        assertThat(rest.getForEntity("/api/v1/notifications/unsubscribe?token=" + token, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<String> post = rest.exchange("/api/v1/notifications/unsubscribe?token=" + token,
                HttpMethod.POST, new HttpEntity<>(null), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from consents where contact = 'late@example.com'", Integer.class);
        assertThat(rows).isZero();
    }
}
