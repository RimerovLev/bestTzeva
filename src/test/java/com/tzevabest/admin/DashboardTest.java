package com.tzevabest.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-13.1: агрегаты дашборда, конверсия, исключение отменённых из спроса, заполнение пропусков ряда. */
class DashboardTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    @Test
    void summary_conversion_counts_all_but_demand_excludes_cancelled() throws Exception {
        long variant = seedVariant("RAL9010");
        // 2 COMPLETED, 1 CANCELLED, 1 NEW → конверсия 50% (знаменатель — все 4)
        seedProductRequest("COMPLETED", variant, "RAL9010", 5);
        seedProductRequest("COMPLETED", variant, "RAL9010", 3);
        seedProductRequest("CANCELLED", variant, "RAL9010", 100);
        seedProductRequest("NEW", variant, "RAL9010", 1);

        JsonNode s = json.readTree(get("/summary").getBody());
        assertThat(s.get("product_requests_today").asInt()).isEqualTo(4);
        assertThat(s.get("product_conversion").asDouble()).isEqualTo(50.0);

        // топ цветов исключает CANCELLED (qty 5+3+1=9, не 109)
        JsonNode colors = json.readTree(get("/top-colors").getBody());
        assertThat(colors).hasSize(1);
        assertThat(colors.get(0).get("key").asText()).isEqualTo("RAL9010");
        assertThat(colors.get(0).get("quantity").asInt()).isEqualTo(9);
    }

    @Test
    void top_products_and_categories_aggregate_demand() throws Exception {
        long variant = seedVariant("RAL7016");
        seedProductRequest("COMPLETED", variant, "RAL7016", 7);
        seedProductRequest("NEW", variant, "RAL7016", 2);

        assertThat(json.readTree(get("/top-products").getBody()).get(0).get("quantity").asInt()).isEqualTo(9);
        assertThat(json.readTree(get("/top-categories").getBody()).get(0).get("quantity").asInt()).isEqualTo(9);
    }

    @Test
    void requests_chart_fills_gaps_with_zeros() throws Exception {
        long variant = seedVariant("RAL1000");
        // заявки на два несмежных дня
        seedProductRequestAt("NEW", variant, "2026-01-01 12:00:00+00");
        seedProductRequestAt("NEW", variant, "2026-01-05 12:00:00+00");

        JsonNode chart = json.readTree(
                get("/requests-chart?from=2026-01-01&to=2026-01-05&granularity=day").getBody());
        int sumProduct = 0;
        int zeros = 0;
        for (JsonNode p : chart) {
            sumProduct += p.get("product_requests").asInt();
            if (p.get("product_requests").asInt() == 0) {
                zeros++;
            }
        }
        assertThat(sumProduct).isEqualTo(2);   // обе заявки учтены
        assertThat(zeros).isGreaterThanOrEqualTo(1); // пустые дни заполнены нулями
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long seedVariant(String color) {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long product = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""", Long.class, cat, brand, "P-" + t);
        return jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, ?, 1.0, 100.00, true) returning id""", Long.class, product, "V-" + t, color);
    }

    private void seedProductRequest(String status, long variant, String color, int qty) {
        long id = insertRequest(status, "now()");
        insertItem(id, variant, color, qty);
    }

    private void seedProductRequestAt(String status, long variant, String createdAt) {
        long id = insertRequest(status, "'" + createdAt + "'");
        insertItem(id, variant, "X", 1);
    }

    private long insertRequest(String status, String createdAtExpr) {
        return jdbcTemplate.queryForObject("insert into product_requests "
                + "(request_number, customer_name, customer_phone, source, consent_given_at, policy_version, status, created_at) "
                + "values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01', ?, " + createdAtExpr + ") returning id",
                Long.class, "PR-" + UUID.randomUUID().toString().substring(0, 10), status);
    }

    private void insertItem(long requestId, long variant, String color, int qty) {
        jdbcTemplate.update("""
                insert into product_request_items (request_id, variant_id, qty, product_name_snapshot,
                        sku_snapshot, price_snapshot, color_snapshot)
                values (?, ?, ?, 'P', 'SKU', 100.00, ?)""", requestId, variant, qty, color);
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@x.test", "he"));
        return rest.exchange("/api/v1/admin/dashboard" + path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }
}
