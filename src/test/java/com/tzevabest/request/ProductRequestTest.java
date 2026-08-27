package com.tzevabest.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-15.1: публичный приём заявки на товар — гостем, отказ без согласия, отказ при неактивном варианте,
 * подстановка цен с сервера, срабатывание антиспама (honeypot).
 */
class ProductRequestTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;

    private long seedVariant(String sku, String price, boolean active) {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-" + sku + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-" + sku + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', ?) returning id""",
                Long.class, categoryId, brandId, "P-" + sku, active);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', ?)",
                productId, "צבע " + sku);
        return jdbcTemplate.queryForObject("""
                        insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                        values (?, ?, 'RAL1', 1.0, ?, ?) returning id""",
                Long.class, productId, sku, new java.math.BigDecimal(price), active);
    }

    @Test
    void guest_creates_request_with_server_side_prices() throws Exception {
        long variantId = seedVariant("V-OK", "120.00", true);
        String body = "{\"customer_name\":\"Ploni\",\"customer_phone\":\"0501234567\",\"consent\":true,"
                + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":2}]}";

        ResponseEntity<String> resp = post(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode node = json.readTree(resp.getBody());
        assertThat(node.get("request_number").asText()).startsWith("PR-");
        assertThat(node.get("status").asText()).isEqualTo("new");
        // цена — с сервера (120), сумма справочная 240
        assertThat(node.get("items").get(0).get("price").asDouble()).isEqualTo(120.0);
        assertThat(node.get("indicative_total").asDouble()).isEqualTo(240.0);

        // снимок цены сохранён; согласие записано
        Integer items = jdbcTemplate.queryForObject(
                "select count(*) from product_request_items where price_snapshot = 120.00", Integer.class);
        assertThat(items).isEqualTo(1);
        Integer consent = jdbcTemplate.queryForObject(
                "select count(*) from consents where source = 'product_request'", Integer.class);
        assertThat(consent).isEqualTo(1);
    }

    @Test
    void refuses_without_consent() throws Exception {
        long variantId = seedVariant("V-NC", "50.00", true);
        String body = "{\"customer_name\":\"P\",\"customer_phone\":\"0502223344\",\"consent\":false,"
                + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":1}]}";
        ResponseEntity<String> resp = post(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(code(resp)).isEqualTo("request.consent_required");
    }

    @Test
    void refuses_inactive_variant() throws Exception {
        long variantId = seedVariant("V-INACTIVE", "50.00", false);
        String body = "{\"customer_name\":\"P\",\"customer_phone\":\"0503334455\",\"consent\":true,"
                + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":1}]}";
        ResponseEntity<String> resp = post(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(code(resp)).isEqualTo("request.variant_unavailable");
    }

    @Test
    void honeypot_rejects_bot_submission() throws Exception {
        long variantId = seedVariant("V-HP", "50.00", true);
        String body = "{\"customer_name\":\"Bot\",\"customer_phone\":\"0504445566\",\"consent\":true,"
                + "\"website\":\"http://spam.example\","
                + "\"items\":[{\"variant_id\":" + variantId + ",\"qty\":1}]}";
        ResponseEntity<String> resp = post(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(code(resp)).isEqualTo("request.spam");
        // заявка не создана
        Integer count = jdbcTemplate.queryForObject("select count(*) from product_requests", Integer.class);
        assertThat(count).isZero();
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/product-requests", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String code(ResponseEntity<String> resp) throws Exception {
        return json.readTree(resp.getBody()).get("code").asText();
    }
}
