package com.tzevabest.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.request.domain.ProductRequestStatus;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.tzevabest.request.domain.ProductRequestStatus.CANCELLED;
import static com.tzevabest.request.domain.ProductRequestStatus.COMPLETED;
import static com.tzevabest.request.domain.ProductRequestStatus.CONTACTED;
import static com.tzevabest.request.domain.ProductRequestStatus.LOST;
import static com.tzevabest.request.domain.ProductRequestStatus.NEW;
import static com.tzevabest.request.domain.ProductRequestStatus.RESERVED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-15.2: матрица переходов заявки (разрешённые 200, запрещённые 409) и постановка/снятие резерва
 * ровно на заявленных переходах.
 */
class ProductRequestAdminTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;
    @Autowired
    private ObjectMapper json;

    private static final Map<ProductRequestStatus, Set<ProductRequestStatus>> ALLOWED = Map.of(
            NEW, EnumSet.of(CONTACTED, CANCELLED, LOST),
            CONTACTED, EnumSet.of(RESERVED, CANCELLED, LOST),
            RESERVED, EnumSet.of(COMPLETED, CANCELLED, LOST),
            COMPLETED, EnumSet.noneOf(ProductRequestStatus.class),
            CANCELLED, EnumSet.noneOf(ProductRequestStatus.class),
            LOST, EnumSet.noneOf(ProductRequestStatus.class));

    private long variantId;
    private long warehouseId;
    private long managerId;

    @BeforeEach
    void seed() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status, locale)
                values (?, '$2a$10$placeholderplaceholderplaceholderplaceholderpl', 'M', 'M', 'MANAGER', 'ACTIVE', 'he')
                returning id""", Long.class, "mgr-" + t + "@tzevabest.test");
        long categoryId = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId, "P-" + t);
        variantId = jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, 'RAL1', 1.0, 100.00, true) returning id""", Long.class, productId, "V-" + t);
        warehouseId = jdbcTemplate.queryForObject("insert into warehouses (code, is_active) values (?, true) returning id",
                Long.class, "WH-" + t);
        jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, 1000, 0)",
                variantId, warehouseId);
    }

    static Stream<Arguments> matrix() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (ProductRequestStatus from : ProductRequestStatus.values()) {
            for (ProductRequestStatus to : ProductRequestStatus.values()) {
                if (from != to) {
                    b.add(Arguments.of(from, to, ALLOWED.get(from).contains(to)));
                }
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("matrix")
    void transition_matrix(ProductRequestStatus from, ProductRequestStatus to, boolean allowed) {
        long id = createRequest(2);
        setStatus(id, from);
        ResponseEntity<String> resp = transition(id, to);
        if (allowed) {
            assertThat(resp.getStatusCode().value()).as("body=%s", resp.getBody()).isEqualTo(200);
        } else {
            assertThat(resp.getStatusCode().value()).isEqualTo(409);
        }
    }

    @Test
    void reserved_reserves_completed_consumes() {
        long id = createRequest(3);
        transition(id, CONTACTED);
        assertThat(transition(id, RESERVED).getStatusCode().value()).isEqualTo(200);
        assertThat(reservedQty()).isEqualTo(3); // резерв поставлен

        assertThat(transition(id, COMPLETED).getStatusCode().value()).isEqualTo(200);
        assertThat(reservedQty()).isZero();      // резерв снят
        assertThat(qty()).isEqualTo(997);         // 1000 - 3 списано
    }

    @Test
    void reserved_then_cancelled_releases_without_consuming() {
        long id = createRequest(4);
        transition(id, CONTACTED);
        transition(id, RESERVED);
        assertThat(reservedQty()).isEqualTo(4);

        assertThat(transition(id, CANCELLED).getStatusCode().value()).isEqualTo(200);
        assertThat(reservedQty()).isZero();   // резерв снят
        assertThat(qty()).isEqualTo(1000);    // ничего не списано
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long createRequest(int qty) {
        String t = UUID.randomUUID().toString().substring(0, 10);
        long id = jdbcTemplate.queryForObject("""
                insert into product_requests (request_number, customer_name, customer_phone, source, consent_given_at, policy_version)
                values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id""",
                Long.class, "PR-" + t);
        jdbcTemplate.update("""
                insert into product_request_items (request_id, variant_id, qty, product_name_snapshot, sku_snapshot, price_snapshot)
                values (?, ?, ?, 'Paint', 'SKU', 100.00)""", id, variantId, qty);
        return id;
    }

    private void setStatus(long id, ProductRequestStatus status) {
        jdbcTemplate.update("update product_requests set status = ? where id = ?", status.name(), id);
    }

    private ResponseEntity<String> transition(long id, ProductRequestStatus to) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "m@tzevabest.test", "he"));
        String body = "{\"to\":\"" + to.name() + "\"}";
        return rest.exchange("/api/v1/admin/product-requests/" + id + "/transitions",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private int reservedQty() {
        return jdbcTemplate.queryForObject(
                "select reserved_qty from inventory where variant_id = ? and warehouse_id = ?",
                Integer.class, variantId, warehouseId);
    }

    private int qty() {
        return jdbcTemplate.queryForObject(
                "select qty from inventory where variant_id = ? and warehouse_id = ?",
                Integer.class, variantId, warehouseId);
    }
}
