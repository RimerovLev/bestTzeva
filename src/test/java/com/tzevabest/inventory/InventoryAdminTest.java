package com.tzevabest.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.service.InventoryService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Складское админ-API: массовый приход, инвентаризация при наличии резервов, запрет деактивации
 * склада с остатками/резервами, матрица доступа по ролям, список/движения/low-stock/экспорт.
 */
class InventoryAdminTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;
    @Autowired
    private InventoryService inventory;

    // ── Массовый приход ───────────────────────────────────────────────────────────

    @Test
    void bulk_receipt_adds_stock_and_writes_movements_and_audit() throws Exception {
        long w = seedWarehouse();
        String sku1 = seedVariantSku();
        String sku2 = seedVariantSku();

        String body = "{\"warehouse_id\":" + w + ",\"comment\":\"delivery #7\",\"lines\":["
                + "{\"sku\":\"" + sku1 + "\",\"qty\":5},{\"sku\":\"" + sku2 + "\",\"qty\":3}]}";
        ResponseEntity<String> resp = post("/api/v1/admin/inventory/receipt", body, "WAREHOUSE");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resp.getBody()).get("received_lines").asInt()).isEqualTo(2);

        assertThat(qtyBySku(sku1, w)).isEqualTo(5);
        assertThat(qtyBySku(sku2, w)).isEqualTo(3);
        Integer receipts = jdbcTemplate.queryForObject(
                "select count(*) from inventory_movements where type = 'RECEIPT' and comment = 'delivery #7'",
                Integer.class);
        assertThat(receipts).isEqualTo(2);
        Integer audits = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'inventory.receipt'", Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void receipt_with_unknown_sku_is_rejected() {
        long w = seedWarehouse();
        String body = "{\"warehouse_id\":" + w + ",\"lines\":[{\"sku\":\"NOPE-404\",\"qty\":1}]}";
        ResponseEntity<String> resp = post("/api/v1/admin/inventory/receipt", body, "MANAGER");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── Инвентаризация при наличии резервов ──────────────────────────────────────

    @Test
    void stocktake_below_reserved_is_rejected_but_above_succeeds() throws Exception {
        long w = seedWarehouse();
        String sku = seedVariantSku();
        long v = variantId(sku);
        inventory.receive(v, w, 10);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 8))); // резерв 8 на активном складе

        String below = "{\"variant_id\":" + v + ",\"warehouse_id\":" + w + ",\"counted_qty\":5,\"reason\":\"audit\"}";
        ResponseEntity<String> rejected = post("/api/v1/admin/inventory/stocktake", below, "WAREHOUSE");
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(rejected.getBody()).get("code").asText())
                .isEqualTo("inventory.stocktake_below_reserved");
        assertThat(qtyBySku(sku, w)).isEqualTo(10); // не изменилось

        String ok = "{\"variant_id\":" + v + ",\"warehouse_id\":" + w + ",\"counted_qty\":9,\"reason\":\"audit\"}";
        assertThat(post("/api/v1/admin/inventory/stocktake", ok, "WAREHOUSE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qtyBySku(sku, w)).isEqualTo(9);
    }

    // ── Списание / перемещение ────────────────────────────────────────────────────

    @Test
    void write_off_and_transfer_move_stock() throws Exception {
        long w1 = seedWarehouse();
        long w2 = seedWarehouse();
        String sku = seedVariantSku();
        long v = variantId(sku);
        inventory.receive(v, w1, 10);

        String wo = "{\"variant_id\":" + v + ",\"warehouse_id\":" + w1 + ",\"qty\":3,\"reason\":\"broken\"}";
        assertThat(post("/api/v1/admin/inventory/write-off", wo, "MANAGER").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qtyBySku(sku, w1)).isEqualTo(7);

        String tr = "{\"variant_id\":" + v + ",\"from_warehouse_id\":" + w1 + ",\"to_warehouse_id\":" + w2
                + ",\"qty\":4}";
        assertThat(post("/api/v1/admin/inventory/transfer", tr, "MANAGER").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qtyBySku(sku, w1)).isEqualTo(3);
        assertThat(qtyBySku(sku, w2)).isEqualTo(4);
    }

    // ── Список / движения / low-stock / экспорт ──────────────────────────────────

    @Test
    void list_shows_exact_numbers_and_status_filter() throws Exception {
        long w = seedWarehouse();
        String sku = seedVariantSku();
        long v = variantId(sku);
        inventory.receive(v, w, 10);

        JsonNode page = json.readTree(get("/api/v1/admin/inventory?warehouse_id=" + w, "WAREHOUSE").getBody());
        assertThat(page.get("total_elements").asInt()).isEqualTo(1);
        JsonNode row = page.get("items").get(0);
        assertThat(row.get("qty").asInt()).isEqualTo(10);       // точные числа наружу — можно
        assertThat(row.get("available").asInt()).isEqualTo(10);
        assertThat(row.get("status").asText()).isEqualTo("in_stock");

        assertThat(json.readTree(get("/api/v1/admin/inventory?status=OUT&warehouse_id=" + w, "WAREHOUSE").getBody())
                .get("total_elements").asInt()).isZero();
    }

    @Test
    void movements_endpoint_lists_journal_and_active_reservations() throws Exception {
        long w = seedWarehouse();
        String sku = seedVariantSku();
        long v = variantId(sku);
        inventory.receive(v, w, 10);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 2)));

        JsonNode body = json.readTree(get("/api/v1/admin/inventory/" + v + "/movements", "WAREHOUSE").getBody());
        assertThat(body.get("movements").get("total_elements").asInt()).isGreaterThanOrEqualTo(2); // receipt + reservation
        assertThat(body.get("active_reservations")).hasSize(1);
        assertThat(body.get("active_reservations").get(0).get("qty").asInt()).isEqualTo(2);
        assertThat(body.get("active_reservations").get(0).get("request_number").asText()).startsWith("PR-");

        // фильтр по типу
        JsonNode receipts = json.readTree(
                get("/api/v1/admin/inventory/" + v + "/movements?type=RECEIPT", "WAREHOUSE").getBody());
        assertThat(receipts.get("movements").get("items").get(0).get("type").asText()).isEqualTo("RECEIPT");
    }

    @Test
    void low_stock_sorted_by_criticality() throws Exception {
        long w = seedWarehouse();
        String skuCrit = seedVariantSku();
        String skuMild = seedVariantSku();
        long crit = variantId(skuCrit);
        long mild = variantId(skuMild);
        inventory.receive(crit, w, 2);
        inventory.receive(mild, w, 8);
        jdbcTemplate.update("update inventory set low_stock_threshold = 10 where warehouse_id = ?", w);

        JsonNode arr = json.readTree(get("/api/v1/admin/inventory/low-stock", "WAREHOUSE").getBody());
        assertThat(arr).hasSize(2);
        // критичнее (2/10) идёт первым, мягче (8/10) — вторым
        assertThat(arr.get(0).get("sku").asText()).isEqualTo(skuCrit);
        assertThat(arr.get(0).get("criticality").asDouble()).isLessThan(arr.get(1).get("criticality").asDouble());
    }

    @Test
    void export_returns_xlsx() {
        long w = seedWarehouse();
        long v = variantId(seedVariantSku());
        inventory.receive(v, w, 4);
        ResponseEntity<byte[]> resp = rest.exchange("/api/v1/admin/inventory/export", HttpMethod.GET,
                new HttpEntity<>(authHeaders("MANAGER")), byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("spreadsheetml");
        assertThat(resp.getBody()).isNotEmpty();
        // .xlsx — это zip: сигнатура PK\x03\x04
        assertThat(resp.getBody()[0]).isEqualTo((byte) 'P');
        assertThat(resp.getBody()[1]).isEqualTo((byte) 'K');
    }

    // ── Склады: деактивация ───────────────────────────────────────────────────────

    @Test
    void warehouse_cannot_be_deactivated_with_stock_then_can_when_empty() throws Exception {
        // создаём склад через API
        String code = "WH-" + uid();
        ResponseEntity<String> created = post("/api/v1/admin/warehouses",
                "{\"code\":\"" + code + "\"}", "MANAGER");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long w = json.readTree(created.getBody()).get("id").asLong();

        long v = variantId(seedVariantSku());
        inventory.receive(v, w, 5);

        // с остатком — запрет
        ResponseEntity<String> blocked = patch("/api/v1/admin/warehouses/" + w + "/active",
                "{\"active\":false}", "MANAGER");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(blocked.getBody()).get("code").asText()).isEqualTo("warehouse.has_stock");

        // убрали остаток — можно
        inventory.writeOff(v, w, 5, "clear", com.tzevabest.inventory.dto.MovementContext.manual(null));
        assertThat(patch("/api/v1/admin/warehouses/" + w + "/active", "{\"active\":false}", "MANAGER")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeFlag(w)).isFalse();
    }

    @Test
    void warehouse_cannot_be_deactivated_with_active_reservations() throws Exception {
        String code = "WH-" + uid();
        long w = json.readTree(post("/api/v1/admin/warehouses", "{\"code\":\"" + code + "\"}", "MANAGER").getBody())
                .get("id").asLong();
        long v = variantId(seedVariantSku());
        inventory.receive(v, w, 10);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 4)));
        // резерв проверяется первым → именно эта ветка отказа
        ResponseEntity<String> blocked = patch("/api/v1/admin/warehouses/" + w + "/active",
                "{\"active\":false}", "MANAGER");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(blocked.getBody()).get("code").asText())
                .isEqualTo("warehouse.has_active_reservations");
    }

    // ── Матрица доступа ───────────────────────────────────────────────────────────

    @Test
    void role_access_matrix() {
        String path = "/api/v1/admin/inventory";
        assertThat(get(path, "WAREHOUSE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(path, "MANAGER").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(path, "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(path, "SUPPORT").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);  // staff, но не склад
        assertThat(get(path, "CUSTOMER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); // не staff
        // без токена
        ResponseEntity<String> anon = rest.getForEntity(path, String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<String> get(String path, String role) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(role)), String.class);
    }

    private ResponseEntity<String> post(String path, String body, String role) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(role)), String.class);
    }

    private ResponseEntity<String> patch(String path, String body, String role) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, jsonHeaders(role)), String.class);
    }

    private Long staffId;

    /** Реальный пользователь для FK inventory_movements.created_by (синтетический id=1 не существует). */
    private long staffUserId() {
        if (staffId == null) {
            staffId = jdbcTemplate.queryForObject("""
                    insert into users (email, password_hash, first_name, last_name, role, status)
                    values (?, 'x', 'S', 'T', 'ADMIN', 'ACTIVE') returning id""",
                    Long.class, "staff-" + uid() + "@tzevabest.test");
        }
        return staffId;
    }

    private HttpHeaders authHeaders(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(staffUserId(), role, role.toLowerCase() + "@tzevabest.test", "he"));
        return h;
    }

    private HttpHeaders jsonHeaders(String role) {
        HttpHeaders h = authHeaders(role);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private long seedWarehouse() {
        return jdbcTemplate.queryForObject(
                "insert into warehouses (code, is_active) values (?, true) returning id", Long.class, "WH-" + uid());
    }

    private String seedVariantSku() {
        String t = uid();
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId, "P-" + t);
        jdbcTemplate.update("insert into product_translations (product_id, locale, name) values (?, 'he', 'צבע')", productId);
        String sku = "SKU-" + t;
        jdbcTemplate.update("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, 'RAL1', 1.0, 100.00, true)""", productId, sku);
        return sku;
    }

    private long variantId(String sku) {
        return jdbcTemplate.queryForObject("select id from product_variants where sku = ?", Long.class, sku);
    }

    private int qtyBySku(String sku, long warehouse) {
        return jdbcTemplate.queryForObject("select i.qty from inventory i join product_variants v on v.id = i.variant_id "
                + "where v.sku = ? and i.warehouse_id = ?", Integer.class, sku, warehouse);
    }

    private boolean activeFlag(long warehouseId) {
        return jdbcTemplate.queryForObject("select is_active from warehouses where id = ?", Boolean.class, warehouseId);
    }

    private long seedRequest() {
        return jdbcTemplate.queryForObject("""
                        insert into product_requests (request_number, customer_name, customer_phone, source,
                                                      consent_given_at, policy_version)
                        values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id""",
                Long.class, "PR-" + UUID.randomUUID().toString().substring(0, 10));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
