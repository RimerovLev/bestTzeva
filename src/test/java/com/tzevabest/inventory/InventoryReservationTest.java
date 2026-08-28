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

    @Test
    void movements_journal_sums_to_qty_and_statuses_distinguish_outcome() {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        long reqA = seedRequest();
        long reqB = seedRequest();

        // приход → резерв → продажа → приход → резерв → снятие
        inventory.receive(variant, warehouse, 10);
        inventory.reserve(reqA, List.of(new ReserveItem(variant, 3)));
        inventory.consume(reqA);
        inventory.receive(variant, warehouse, 5);
        inventory.reserve(reqB, List.of(new ReserveItem(variant, 4)));
        inventory.release(reqB);

        // журнал сходится: sum(qty_delta) == inventory.qty (10 - 3 + 5 = 12)
        Integer sumDelta = jdbcTemplate.queryForObject(
                "select coalesce(sum(qty_delta),0) from inventory_movements where variant_id = ? and warehouse_id = ?",
                Integer.class, variant, warehouse);
        assertThat(sumDelta).isEqualTo(qty(variant, warehouse)).isEqualTo(12);

        // проданный резерв отличим от отменённого (R-50)
        assertThat(reservationStatus(reqA)).isEqualTo("CONSUMED");
        assertThat(reservationStatus(reqB)).isEqualTo("RELEASED");
    }

    @Test
    void parallel_release_is_idempotent() throws Exception {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(variant, warehouse, 5);
        long request = seedRequest();
        inventory.reserve(request, List.of(new ReserveItem(variant, 5)));
        assertThat(reservedQty(variant, warehouse)).isEqualTo(5);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = List.of(
                () -> { inventory.releaseExpired(request); return null; },
                () -> { inventory.releaseExpired(request); return null; });
        for (Future<Void> f : pool.invokeAll(tasks)) {
            f.get();
        }
        pool.shutdown();

        // снято ровно один раз — reserved_qty не ушёл в минус и не вычтен дважды
        assertThat(reservedQty(variant, warehouse)).isZero();
    }

    @Test
    void sweep_processes_all_expired_in_batches() {
        long variant = seedVariant();
        long warehouse = seedWarehouse(true);
        jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, 250, 250)",
                variant, warehouse);
        jdbcTemplate.update("""
                insert into product_requests (request_number, customer_name, customer_phone, source, consent_given_at, policy_version, status)
                select 'PR-B-' || g, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01', 'RESERVED'
                from generate_series(1, 250) g""");
        jdbcTemplate.update("""
                insert into stock_reservations (variant_id, warehouse_id, request_id, qty, status, expires_at)
                select ?, ?, r.id, 1, 'ACTIVE', now() - interval '1 hour'
                from product_requests r where r.request_number like 'PR-B-%'""", variant, warehouse);

        int swept = expiry.sweep();
        assertThat(swept).isEqualTo(250); // 250 > batch(100) → обработано пачками, все сняты
        assertThat(reservedQty(variant, warehouse)).isZero();
        Integer lost = jdbcTemplate.queryForObject(
                "select count(*) from product_requests where request_number like 'PR-B-%' and status = 'LOST'", Integer.class);
        assertThat(lost).isEqualTo(250);
    }

    @Test
    void crossing_reservations_do_not_deadlock() throws Exception {
        long x = seedVariant();
        long y = seedVariant();
        long warehouse = seedWarehouse(true);
        seedInventory(x, warehouse, 100);
        seedInventory(y, warehouse, 100);
        long reqA = seedRequest();
        long reqB = seedRequest();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        // A резервирует X,Y ; B — Y,X. reserve() сортирует по variant_id → одинаковый порядок блокировок.
        Callable<Void> a = () -> {
            for (int i = 0; i < 20; i++) {
                inventory.reserve(reqA, List.of(new ReserveItem(x, 1), new ReserveItem(y, 1)));
            }
            return null;
        };
        Callable<Void> b = () -> {
            for (int i = 0; i < 20; i++) {
                inventory.reserve(reqB, List.of(new ReserveItem(y, 1), new ReserveItem(x, 1)));
            }
            return null;
        };
        for (Future<Void> f : pool.invokeAll(List.of(a, b))) {
            f.get(); // бросит, если словили дедлок — не должно
        }
        pool.shutdown();

        assertThat(reservedQty(x, warehouse)).isEqualTo(40);
        assertThat(reservedQty(y, warehouse)).isEqualTo(40);
    }

    private String reservationStatus(long request) {
        return jdbcTemplate.queryForObject(
                "select status from stock_reservations where request_id = ? order by id limit 1", String.class, request);
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

    private int qty(long variant, long warehouse) {
        return jdbcTemplate.queryForObject(
                "select qty from inventory where variant_id = ? and warehouse_id = ?",
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
