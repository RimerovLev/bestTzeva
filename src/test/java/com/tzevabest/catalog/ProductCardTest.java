package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-4.4: публичная карточка товара — активные варианты с доступным остатком и признаком «можно
 * добавить в заявку», денормализованные цена/наличие, HE-fallback, без рейтинга. Неактивный — 404.
 */
class ProductCardTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;

    @Test
    void active_product_card_shows_variants_with_availability_and_no_rating() throws Exception {
        long productId = seedProduct(true, "צבע", "Paint");
        long warehouse = jdbcTemplate.queryForObject(
                "insert into warehouses (code, is_active) values (?, true) returning id", Long.class,
                "WH-" + uid());
        long a = seedVariant(productId, "A", "100.00", true);
        long b = seedVariant(productId, "B", "150.00", true);
        seedVariant(productId, "C", "999.00", false);            // неактивный — скрыт
        jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, 5, 0)", a, warehouse);
        jdbcTemplate.execute("select refresh_product_denorm(" + productId + ")");

        ResponseEntity<String> resp = rest.getForEntity("/api/v1/products/" + productId, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode card = json.readTree(resp.getBody());

        assertThat(card.get("variants")).hasSize(2);             // только A и B
        JsonNode va = variant(card, a);
        JsonNode vb = variant(card, b);
        // градация вместо точного числа (R-47): 5 из 10-порога → few, 0 → out_of_stock
        assertThat(va.get("availability").asText()).isEqualTo("few");
        assertThat(va.get("can_add_to_request").asBoolean()).isTrue();
        assertThat(vb.get("availability").asText()).isEqualTo("out_of_stock");
        assertThat(vb.get("can_add_to_request").asBoolean()).isFalse();
        // точного числа остатка наружу нет
        assertThat(va.has("available_qty")).isFalse();

        assertThat(card.get("min_price").asDouble()).isEqualTo(100.0);
        assertThat(card.get("max_price").asDouble()).isEqualTo(150.0);
        assertThat(card.get("has_stock").asBoolean()).isTrue();
        assertThat(card.has("rating_avg")).isFalse();
        assertThat(card.has("rating_count")).isFalse();
    }

    @Test
    void inactive_product_returns_404() {
        long productId = seedProduct(false, "צבע", "Paint");
        seedVariant(productId, "X", "100.00", true);
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/products/" + productId, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missing_translation_falls_back_to_he() throws Exception {
        long productId = seedProduct(true, "צבע עברית", null);      // только HE
        seedVariant(productId, "V", "100.00", true);
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/products/" + productId + "?locale=ru", String.class);
        assertThat(json.readTree(resp.getBody()).get("name").asText()).isEqualTo("צבע עברית");
    }

    private long seedProduct(boolean active, String heName, String enName) {
        String t = uid();
        long categoryId = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', ?) returning id""",
                Long.class, categoryId, brandId, "P-" + t, active);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', ?)", productId, heName);
        if (enName != null) {
            jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'en', ?)", productId, enName);
        }
        return productId;
    }

    private long seedVariant(long productId, String suffix, String price, boolean active) {
        return jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, ?, 1.0, ?, ?) returning id""",
                Long.class, productId, "V-" + uid() + suffix, "RAL" + suffix, new java.math.BigDecimal(price), active);
    }

    private JsonNode variant(JsonNode card, long id) {
        for (JsonNode v : card.get("variants")) {
            if (v.get("id").asLong() == id) {
                return v;
            }
        }
        throw new AssertionError("variant not found: " + id);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
