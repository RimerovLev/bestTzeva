package com.tzevabest.request;

import com.tzevabest.support.IntegrationTest;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-52: лимит по телефону не должен обходиться сменой формата номера — четыре записи одного номера
 * тратят один бюджет. Клиент без авто-ретраев (иначе HttpClient5 при 429 c Retry-After «уснёт»).
 */
class ProductRequestRateLimitTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate rest;

    @BeforeEach
    void initClient() {
        var factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableAutomaticRetries().build());
        rest = new RestTemplate(factory);
        rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // статус читаем сами
            }
        });
    }

    @Test
    void phone_rate_limit_ignores_number_format() {
        // Один и тот же номер в четырёх форматах — общий бюджет 3/час, четвёртый отклонён.
        String[] phones = {"0501112233", "050-111-2233", "+972501112233", "972-50-111-2233"};
        int[] got = new int[4];
        for (int i = 0; i < 4; i++) {
            long variantId = seedVariant("RL" + i);
            String body = "{\"customer_name\":\"P\",\"customer_phone\":\"" + phones[i] + "\",\"consent\":true,"
                    + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":1}]}";
            got[i] = post(body).getStatusCode().value();
        }
        assertThat(got[0]).isEqualTo(201);
        assertThat(got[1]).isEqualTo(201);
        assertThat(got[2]).isEqualTo(201);
        assertThat(got[3]).isEqualTo(429); // четвёртый по тому же номеру
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://localhost:" + port + "/api/v1/product-requests",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private long seedVariant(String sku) {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-" + sku + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-" + sku + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId, "P-" + sku);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', 'צבע')", productId);
        return jdbcTemplate.queryForObject("""
                        insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                        values (?, ?, 'RAL1', 1.0, 50.00, true) returning id""", Long.class, productId, sku);
    }
}
