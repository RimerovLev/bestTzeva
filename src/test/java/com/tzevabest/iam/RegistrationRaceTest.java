package com.tzevabest.iam;

import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.service.AuthService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-26: две одновременные регистрации на один email дают ровно один аккаунт и один 409 с кодом
 * common.auth.contact_taken (а не общий data.conflict от уникального индекса).
 */
class RegistrationRaceTest extends IntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    void concurrent_registration_on_same_email_yields_one_account_and_contact_taken() throws Exception {
        String email = "race-" + UUID.randomUUID().toString().substring(0, 8) + "@tzevabest.test";
        Callable<Object> register = () -> {
            try {
                authService.register(new RegisterRequest(email, null, "Passw0rd", "A", "B", null), "ua", "1.1.1.1");
                return "OK";
            } catch (Exception e) {
                return e;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> f1 = pool.submit(register);
        Future<Object> f2 = pool.submit(register);
        List<Object> results = List.of(f1.get(), f2.get());
        pool.shutdown();

        // ровно один аккаунт
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where lower(email) = lower(?)", Integer.class, email);
        assertThat(count).isEqualTo(1);

        // ровно один успех и один конфликт с кодом contact_taken
        assertThat(results).filteredOn("OK"::equals).hasSize(1);
        Object failure = results.stream().filter(r -> r instanceof Throwable).findFirst().orElseThrow();
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).getCode()).isEqualTo("common.auth.contact_taken");
    }
}
