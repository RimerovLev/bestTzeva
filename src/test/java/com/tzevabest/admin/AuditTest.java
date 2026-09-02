package com.tzevabest.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.admin.service.AuditRetentionService;
import com.tzevabest.admin.service.AuditService;
import com.tzevabest.iam.service.JwtService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-13.3: diff при изменении цены, исключение чувствительных полей, эндпоинт только для ADMIN, ретеншен. */
class AuditTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditRetentionService retention;

    @Test
    void product_price_change_writes_diff() throws Exception {
        long[] ids = seedVariant();
        long productId = ids[0];
        long variantId = ids[1];

        ResponseEntity<String> patched = rest.exchange(
                "/api/v1/admin/products/variants/" + variantId + "/price", HttpMethod.PATCH,
                new HttpEntity<>("{\"price\":150}", jsonHeaders("MANAGER")), String.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode history = json.readTree(
                get("/api/v1/admin/audit-log/entity/product/" + productId, "ADMIN").getBody());
        assertThat(history).isNotEmpty();
        JsonNode entry = history.get(0);
        assertThat(entry.get("action").asText()).isEqualTo("VARIANT_PRICE");
        assertThat(entry.get("changes").has("price")).isTrue(); // diff по цене записан
    }

    @Test
    void sensitive_fields_are_excluded_from_changes() throws Exception {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("password", "topsecret");
        changes.put("reset_token", "abc123");
        changes.put("customer_phone", "+972501234567");
        changes.put("customer_email", "ivan@example.com");
        changes.put("price", 100);
        auditService.record(1L, "1.2.3.4", "test.sensitive", "thing", 7L, changes);

        JsonNode list = json.readTree(get("/api/v1/admin/audit-log?action=test.sensitive", "ADMIN").getBody());
        JsonNode ch = list.get("items").get(0).get("changes");
        assertThat(ch.has("password")).isFalse();      // секрет не записан
        assertThat(ch.has("reset_token")).isFalse();   // токен не записан
        assertThat(ch.get("customer_phone").asText()).isEqualTo("***4567"); // телефон замаскирован
        assertThat(ch.get("customer_email").asText()).isEqualTo("i***@example.com"); // email замаскирован (R-59)
        assertThat(ch.get("price").asInt()).isEqualTo(100); // обычное поле сохранено
    }

    @Test
    void audit_log_endpoint_is_admin_only() {
        assertThat(get("/api/v1/admin/audit-log", "MANAGER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/audit-log", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void retention_purges_records_older_than_ttl() {
        // старая запись (>2 лет) и свежая
        jdbcTemplate.update("insert into audit_log (action, entity_type, created_at) "
                + "values ('old.marker', 'thing', now() - interval '800 days')");
        auditService.record(1L, "1.1.1.1", "fresh.marker", "thing", 1L, Map.of("x", 1));

        int purged = retention.purge();
        assertThat(purged).isGreaterThanOrEqualTo(1);
        Integer oldLeft = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'old.marker'", Integer.class);
        assertThat(oldLeft).isZero();
        Integer freshLeft = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'fresh.marker'", Integer.class);
        assertThat(freshLeft).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long[] seedVariant() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long product = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""", Long.class, cat, brand, "P-" + t);
        long variant = jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, 'RAL1', 1.0, 100.00, true) returning id""", Long.class, product, "V-" + t);
        return new long[]{product, variant};
    }

    private ResponseEntity<String> get(String path, String role) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(role)), String.class);
    }

    private HttpHeaders authHeaders(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(1L, role, role.toLowerCase() + "@x.test", "he"));
        return h;
    }

    private HttpHeaders jsonHeaders(String role) {
        HttpHeaders h = authHeaders(role);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
