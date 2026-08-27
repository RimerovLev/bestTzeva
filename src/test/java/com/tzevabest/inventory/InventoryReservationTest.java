package com.tzevabest.inventory;

import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.service.InventoryService;
import com.tzevabest.request.service.ReservationExpiryService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-5.2: резервирование остатков под заявку — постановка/снятие, конкуренция за последние банки,
 * истечение резерва с переводом заявки в LOST.
 */
class InventoryReservationTest extends IntegrationTest {

    @Autowired
    private InventoryService inventory;
    @Autowired
    private ReservationExpiryService expiry;

    @Test
    void reserve_reduces_available_and_release_restores() {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(variant, warehouse, 10);
        long request = seedRequest();

        inventory.reserve(request, List.of(new ReserveItem(variant, 4)));
        assertThat(reservedQty(variant, warehouse)).isEqualTo(4);
        assertThat(activeReservations(request)).isEqualTo(1);

        inventory.release(request);
        assertThat(reservedQty(variant, warehouse)).isZero();
        assertThat(activeReservations(request)).isZero();
    }

    @Test
    void insufficient_stock_is_rejected_and_rolled_back() {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(variant, warehouse, 2);
        long request = seedRequest();

        assertThat(catchReserve(request, variant, 5)).isTrue(); // 5 > 2 → отказ
        assertThat(reservedQty(variant, warehouse)).isZero();   // ничего не занято
    }

    @Test
    void concurrent_reservations_for_last_can_leave_exactly_available() throws Exception {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(variant, warehouse, 1); // одна банка

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger();
        List<Callable<Void>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long request = seedRequest();
            tasks.add(() -> {
                try {
                    inventory.reserve(request, List.of(new ReserveItem(variant, 1)));
                    success.incrementAndGet();
                } catch (ConflictException ignored) {
                    // проиграл гонку — ожидаемо
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(tasks)) {
            f.get();
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);           // выиграл ровно один
        assertThat(reservedQty(variant, warehouse)).isEqualTo(1);
    }

    @Test
    void expired_reservation_is_released_and_request_becomes_lost() {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(variant, warehouse, 10);
        long request = seedRequest();
        jdbcTemplate.update("update product_requests set status = 'RESERVED' where id = ?", request);

        inventory.reserve(request, List.of(new ReserveItem(variant, 3)));
        // искусственно просрочиваем резерв
        jdbcTemplate.update("update stock_reservations set expires_at = now() - interval '1 hour' where request_id = ?", request);

        int swept = expiry.sweep();
        assertThat(swept).isEqualTo(1);
        assertThat(reservedQty(variant, warehouse)).isZero();
        assertThat(status(request)).isEqualTo("LOST");
        Integer expiredRows = jdbcTemplate.queryForObject(
                "select count(*) from stock_reservations where request_id = ? and status = 'EXPIRED'",
                Integer.class, request);
        assertThat(expiredRows).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private boolean catchReserve(long request, long variant, int qty) {
        try {
            inventory.reserve(request, List.of(new ReserveItem(variant, qty)));
            return false;
        } catch (ConflictException e) {
            return true;
        }
    }

    private long seedVariant() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = jdbcTemplate.queryForObject(
                "insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject(
                "insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                        insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                        values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId, "P-" + t);
        return jdbcTemplate.queryForObject("""
                        insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                        values (?, ?, 'RAL1', 1.0, 100.00, true) returning id""",
                Long.class, productId, "V-" + t);
    }

    private long seedWarehouse(boolean active) {
        String t = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "insert into warehouses (code, is_active) values (?, ?) returning id", Long.class, "WH-" + t, active);
    }

    private void seedInventory(long variant, long warehouse, int qty) {
        jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, 0)",
                variant, warehouse, qty);
    }

    private long seedRequest() {
        String t = UUID.randomUUID().toString().substring(0, 10);
        return jdbcTemplate.queryForObject("""
                        insert into product_requests (request_number, customer_name, customer_phone, source,
                                                      consent_given_at, policy_version)
                        values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id""",
                Long.class, "PR-" + t);
    }

    private int reservedQty(long variant, long warehouse) {
        return jdbcTemplate.queryForObject(
                "select reserved_qty from inventory where variant_id = ? and warehouse_id = ?",
                Integer.class, variant, warehouse);
    }

    private int activeReservations(long request) {
        return jdbcTemplate.queryForObject(
                "select count(*) from stock_reservations where request_id = ? and status = 'ACTIVE'",
                Integer.class, request);
    }

    private String status(long request) {
        return jdbcTemplate.queryForObject("select status from product_requests where id = ?", String.class, request);
    }
}
