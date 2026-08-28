package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-4.5: публичный листинг каталога — фильтры, фасеты по выборке, поиск с поддержкой иврита
 * (unaccent — нечувствительно к никуд), исключение неактивных.
 */
class ProductSearchTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;

    private long brand1;
    private long brand2;

    private void seedCatalog() {
        brand1 = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('br1-" + uid() + "','B1') returning id", Long.class);
        brand2 = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('br2-" + uid() + "','B2') returning id", Long.class);
        seedProduct(brand1, "INTERIOR", "100.00", true, "צבע כחול");
        seedProduct(brand1, "EXTERIOR", "200.00", true, "צבע לבן");
        seedProduct(brand2, "INTERIOR", "300.00", true, "Blue paint");
        seedProduct(brand2, "INTERIOR", "999.00", false, "Hidden"); // неактивный
    }

    @Test
    void lists_active_products_with_facets() throws Exception {
        seedCatalog();
        JsonNode res = get("/api/v1/products");
        assertThat(res.get("products").get("total_elements").asInt()).isEqualTo(3);
        JsonNode facets = res.get("facets");
        assertThat(facets.get("paint_type").get("INTERIOR").asInt()).isEqualTo(2);
        assertThat(facets.get("paint_type").get("EXTERIOR").asInt()).isEqualTo(1);
        assertThat(facets.get("paint_type").has("HIDDEN")).isFalse();
        assertThat(facets.get("brand").get(String.valueOf(brand1)).asInt()).isEqualTo(2);
        assertThat(facets.get("price_min").asDouble()).isEqualTo(100.0);
        assertThat(facets.get("price_max").asDouble()).isEqualTo(300.0);
    }

    @Test
    void filters_by_brand_paint_type_and_price() throws Exception {
        seedCatalog();
        assertThat(get("/api/v1/products?brand_id=" + brand1).get("products").get("total_elements").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/products?paint_type=interior").get("products").get("total_elements").asInt()).isEqualTo(2);
        // max_price >= 250 → только товар за 300
        assertThat(get("/api/v1/products?price_min=250").get("products").get("total_elements").asInt()).isEqualTo(1);
    }

    @Test
    void facets_exclude_their_own_filter() throws Exception {
        seedCatalog();
        JsonNode res = get("/api/v1/products?paint_type=INTERIOR");
        // выборка сужена типом
        assertThat(res.get("products").get("total_elements").asInt()).isEqualTo(2);
        JsonNode facets = res.get("facets");
        // фасет paint_type игнорирует свой фильтр → показывает все типы (чтобы можно было переключиться)
        assertThat(facets.get("paint_type").get("INTERIOR").asInt()).isEqualTo(2);
        assertThat(facets.get("paint_type").get("EXTERIOR").asInt()).isEqualTo(1);
        // фасет бренда сужен фильтром по типу: у каждого бренда по одному INTERIOR
        assertThat(facets.get("brand").get(String.valueOf(brand1)).asInt()).isEqualTo(1);
        assertThat(facets.get("brand").get(String.valueOf(brand2)).asInt()).isEqualTo(1);

        // ползунок цены не схлопывается своим же фильтром
        JsonNode byPrice = get("/api/v1/products?price_min=250");
        assertThat(byPrice.get("products").get("total_elements").asInt()).isEqualTo(1);
        assertThat(byPrice.get("facets").get("price_min").asDouble()).isEqualTo(100.0);
        assertThat(byPrice.get("facets").get("price_max").asDouble()).isEqualTo(300.0);
    }

    @Test
    void hebrew_search_matches_by_name() throws Exception {
        seedCatalog();
        JsonNode res = get("/api/v1/products?q=כחול");
        assertThat(res.get("products").get("total_elements").asInt()).isEqualTo(1);
        assertThat(res.get("products").get("items").get(0).get("name").asText()).isEqualTo("צבע כחול");
    }

    @Test
    void search_is_niqqud_insensitive() throws Exception {
        brand1 = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('brn-" + uid() + "','B') returning id", Long.class);
        // имя с огласовками (никуд)
        seedProduct(brand1, "INTERIOR", "100.00", true, "צֶבַע");
        // запрос без огласовок должен найти
        JsonNode res = get("/api/v1/products?q=צבע");
        assertThat(res.get("products").get("total_elements").asInt()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void seedProduct(long brandId, String paintType, String price, boolean active, String heName) {
        String t = uid();
        long categoryId = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, ?, 'INDOOR','MATTE','WATER', ?) returning id""",
                Long.class, categoryId, brandId, "P-" + t, paintType, active);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', ?)", productId, heName);
        jdbcTemplate.update("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, 'RAL1', 1.0, ?, true)""", productId, "V-" + t, new BigDecimal(price));
    }

    private JsonNode get(String path) throws Exception {
        return json.readTree(rest.getForEntity(path, String.class).getBody());
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
