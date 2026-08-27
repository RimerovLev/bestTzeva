package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory.TestOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Управление товарами-агрегатами: создание целиком, конфликт SKU, деактивация варианта с историей
 * заказов, массовое изменение цен и корректность денормализованных полей после каждой операции.
 */
class ProductManagementTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;

    private long categoryId;
    private long brandId;

    private void seedRefs() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('cat-p') returning id", Long.class);
        brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('brand-p','B') returning id", Long.class);
    }

    // ── Создание агрегата + денорм ───────────────────────────────────────────────

    @Test
    void creates_full_aggregate_with_denormalized_fields() throws Exception {
        seedRefs();
        String body = productBody("P-1", List.of(variant("V-1a", "RAL1", "100.00"), variant("V-1b", "RAL2", "150.00")));
        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/products", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode created = json.readTree(resp.getBody());
        long id = created.get("id").asLong();
        assertThat(created.get("variants")).hasSize(2);
        assertThat(created.get("translations").has("he")).isTrue();
        assertThat(created.get("attributes").get("gloss").asText()).isEqualTo("high");
        assertThat(created.get("min_price").asDouble()).isEqualTo(100.0);
        assertThat(created.get("max_price").asDouble()).isEqualTo(150.0);
        assertThat(created.get("has_stock").asBoolean()).isFalse();

        assertDenorm(id, "100.00", "150.00", false);
    }

    // ── Конфликт SKU ─────────────────────────────────────────────────────────────

    @Test
    void duplicate_variant_sku_is_rejected() throws Exception {
        seedRefs();
        admin(HttpMethod.POST, "/api/v1/admin/products", productBody("P-A", List.of(variant("VDUP", "RAL1", "100.00"))));

        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/products",
                productBody("P-B", List.of(variant("VDUP", "RAL1", "100.00"))));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(resp)).isEqualTo("catalog.sku.duplicate");
    }

    // ── Деактивация варианта с историей заказов ──────────────────────────────────

    @Test
    void variant_with_order_history_is_deactivated_not_deleted_on_update() throws Exception {
        seedRefs();
        JsonNode created = json.readTree(admin(HttpMethod.POST, "/api/v1/admin/products",
                productBody("P-ORD", List.of(variant("V-KEEP", "RAL1", "100.00"), variant("V-HIST", "RAL2", "200.00")))).getBody());
        long id = created.get("id").asLong();
        long keepId = created.get("variants").get(0).get("id").asLong();
        long histId = created.get("variants").get(1).get("id").asLong();

        // по варианту V-HIST есть позиция заказа
        TestOrder order = factory.createOrder();
        jdbcTemplate.update("""
                        insert into order_items (order_id, variant_id, product_name_snapshot, sku_snapshot, unit_price, qty, line_total)
                        values (?, ?, 'Paint', 'V-HIST', 200.00, 1, 200.00)""",
                order.id(), histId);

        // обновляем агрегат, оставляя только V-KEEP
        ResponseEntity<String> resp = admin(HttpMethod.PUT, "/api/v1/admin/products/" + id,
                updateBody("P-ORD", List.of(variantWithId(keepId, "V-KEEP", "RAL1", "100.00"))));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // V-HIST не удалён, а деактивирован
        Boolean histActive = jdbcTemplate.queryForObject(
                "select is_active from product_variants where id = ?", Boolean.class, histId);
        assertThat(histActive).isFalse();
        Integer histRows = jdbcTemplate.queryForObject(
                "select count(*) from product_variants where id = ?", Integer.class, histId);
        assertThat(histRows).isEqualTo(1);

        // денорм — только по активным вариантам (остался V-KEEP = 100)
        assertDenorm(id, "100.00", "100.00", false);
    }

    // ── Массовое изменение цен + денорм ──────────────────────────────────────────

    @Test
    void bulk_price_change_updates_prices_and_denorm() throws Exception {
        seedRefs();
        long id = json.readTree(admin(HttpMethod.POST, "/api/v1/admin/products",
                productBody("P-BULK", List.of(variant("V-BULK", "RAL1", "100.00")))).getBody()).get("id").asLong();

        String bulk = "{\"brand_id\":" + brandId + ",\"percent\":5}";
        ResponseEntity<String> resp = admin(HttpMethod.POST, "/api/v1/admin/products/bulk-price", bulk);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resp.getBody()).get("affected_variants").asInt()).isEqualTo(1);

        // цена варианта поднялась на 5%, денорм пересчитан триггером при массовом UPDATE
        BigDecimal price = jdbcTemplate.queryForObject(
                "select price from product_variants where product_id = ?", BigDecimal.class, id);
        assertThat(price).isEqualByComparingTo("105.00");
        assertDenorm(id, "105.00", "105.00", false);
    }

    // ── Денорм после точечного PATCH цены ────────────────────────────────────────

    @Test
    void variant_price_patch_updates_denorm() throws Exception {
        seedRefs();
        JsonNode created = json.readTree(admin(HttpMethod.POST, "/api/v1/admin/products",
                productBody("P-PATCH", List.of(variant("V-PATCH", "RAL1", "100.00")))).getBody());
        long id = created.get("id").asLong();
        long variantId = created.get("variants").get(0).get("id").asLong();

        admin(HttpMethod.PATCH, "/api/v1/admin/products/variants/" + variantId + "/price", "{\"price\":200.00}");
        assertDenorm(id, "200.00", "200.00", false);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void assertDenorm(long id, String min, String max, boolean hasStock) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select min_price, max_price, has_stock from products where id = ?", id);
        assertThat((BigDecimal) row.get("min_price")).isEqualByComparingTo(min);
        assertThat((BigDecimal) row.get("max_price")).isEqualByComparingTo(max);
        assertThat((Boolean) row.get("has_stock")).isEqualTo(hasStock);
    }

    private String productBody(String sku, List<ObjectNode> variants) {
        ObjectNode node = baseProduct(sku, variants);
        return node.toString();
    }

    private String updateBody(String sku, List<ObjectNode> variants) {
        return baseProduct(sku, variants).toString();
    }

    private ObjectNode baseProduct(String sku, List<ObjectNode> variants) {
        ObjectNode node = json.createObjectNode();
        node.put("category_id", categoryId);
        node.put("brand_id", brandId);
        node.put("sku", sku);
        node.put("paint_type", "INTERIOR");
        node.put("usage_area", "INDOOR");
        node.put("finish", "MATTE");
        node.put("base_type", "WATER");
        node.putObject("translations").putObject("he").put("name", "צבע");
        ((ObjectNode) node.get("translations")).putObject("en").put("name", "Paint");
        ArrayNode attrs = node.putArray("attributes");
        attrs.addObject().put("code", "gloss").put("value", "high");
        ArrayNode vs = node.putArray("variants");
        variants.forEach(vs::add);
        return node;
    }

    private ObjectNode variant(String sku, String colorCode, String price) {
        ObjectNode v = json.createObjectNode();
        v.put("sku", sku);
        v.put("color_code", colorCode);
        v.put("color_hex", "#FFAA00");
        v.put("volume_liters", 1.0);
        v.put("price", new BigDecimal(price));
        return v;
    }

    private ObjectNode variantWithId(long id, String sku, String colorCode, String price) {
        ObjectNode v = variant(sku, colorCode, price);
        v.put("id", id);
        return v;
    }

    private ResponseEntity<String> admin(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@tzevabest.test", "he"));
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String code(ResponseEntity<String> resp) throws Exception {
        return json.readTree(resp.getBody()).get("code").asText();
    }
}
