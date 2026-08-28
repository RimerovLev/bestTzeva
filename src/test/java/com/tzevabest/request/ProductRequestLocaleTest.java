package com.tzevabest.request;

import com.tzevabest.notification.service.NotificationSender;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * R-53: язык заявки берётся из Accept-Language, сохраняется в product_requests.locale и на нём уходит
 * клиентское подтверждение — а не всегда на иврите.
 */
class ProductRequestLocaleTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @MockBean
    private NotificationSender notifications;

    @Test
    void accept_language_ru_stores_locale_and_notifies_in_russian() {
        long variantId = seedVariant();
        String body = "{\"customer_name\":\"Иван\",\"customer_phone\":\"0501234567\","
                + "\"customer_email\":\"ivan@example.com\",\"consent\":true,"
                + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":1}]}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "ru");
        ResponseEntity<String> resp = rest.exchange("/api/v1/product-requests", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // локаль сохранена
        String locale = jdbcTemplate.queryForObject(
                "select locale from product_requests order by id desc limit 1", String.class);
        assertThat(locale).isEqualTo("ru");

        // подтверждение ушло на русском (событие после коммита)
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifications, timeout(2000)).sendEmail(
                org.mockito.ArgumentMatchers.eq("ivan@example.com"), subject.capture(), text.capture());
        assertThat(subject.getValue()).contains("заявка");
        assertThat(text.getValue()).contains("менеджер свяжется");
    }

    private long seedVariant() {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-loc') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-loc','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, 'P-LOC', 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', 'צבע')", productId);
        return jdbcTemplate.queryForObject("""
                        insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                        values (?, 'V-LOC', 'RAL1', 1.0, 50.00, true) returning id""", Long.class, productId);
    }
}
