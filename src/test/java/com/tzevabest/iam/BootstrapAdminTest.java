package com.tzevabest.iam;

import com.tzevabest.iam.service.AdminBootstrap;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-16.2: первый администратор создаётся стартовым компонентом на ПУСТОЙ таблице users из переменных
 * окружения; повторный запуск на непустой базе ничего не делает.
 */
@TestPropertySource(properties = {
        "app.bootstrap.admin-email=boot-admin@tzevabest.test",
        "app.bootstrap.admin-password=Sup3rSecretPass"
})
class BootstrapAdminTest extends IntegrationTest {

    @Autowired
    private AdminBootstrap bootstrap;

    @Test
    void creates_first_admin_on_empty_table_and_is_idempotent() {
        jdbcTemplate.update("truncate table users cascade");

        bootstrap.run(null);
        String role = jdbcTemplate.queryForObject(
                "select role from users where email = 'boot-admin@tzevabest.test'", String.class);
        assertThat(role).isEqualTo("ADMIN");

        // повторный запуск на непустой базе не создаёт второго администратора
        bootstrap.run(null);
        Integer count = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
