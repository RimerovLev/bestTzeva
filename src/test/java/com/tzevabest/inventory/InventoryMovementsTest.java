package com.tzevabest.inventory;

import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.inventory.dto.MovementContext;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.event.LowStockEvent;
import com.tzevabest.inventory.service.InventoryService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Движения физического остатка: списание, инвентаризация, перемещение; корректность журнала
 * (qty_after/reserved_after), запрет отрицательного остатка, отказ инвентаризации ниже резерва,
 * событие LowStockEvent, сходимость журнала с остатком после смешанной последовательности.
 */
@Import(InventoryMovementsTest.TestListeners.class)
class InventoryMovementsTest extends IntegrationTest {

    @Autowired
    private InventoryService inventory;
    @Autowired
    private LowStockCapture lowStock;

    private static final MovementContext CTX = MovementContext.manual(null);

    // ── Списание ─────────────────────────────────────────────────────────────────

    @Test
    void write_off_reduces_qty_and_records_movement() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);

        inventory.writeOff(v, w, 3, "damaged", CTX);

        assertThat(qty(v, w)).isEqualTo(7);
        Map<String, Object> m = lastMovement(v, w);
        assertThat(m.get("type")).isEqualTo("WRITE_OFF");
        assertThat(m.get("qty_delta")).isEqualTo(-3);
        assertThat(m.get("qty_after")).isEqualTo(7);
        assertThat(m.get("reserved_after")).isEqualTo(0);
        assertThat(m.get("comment")).isEqualTo("damaged");
    }

    @Test
    void write_off_cannot_exceed_available_and_keeps_reserved_untouched() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 5);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 4))); // доступно 1

        catchThrowableOfType(() -> inventory.writeOff(v, w, 2, "x", CTX), ConflictException.class);
        assertThat(qty(v, w)).isEqualTo(5);          // ничего не списано
        assertThat(reserved(v, w)).isEqualTo(4);
    }

    @Test
    void write_off_requires_reason_and_positive_qty() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);
        assertThat(catchThrowableOfType(() -> inventory.writeOff(v, w, 3, " ", CTX), ValidationException.class).getCode()).isEqualTo("inventory.writeoff_reason_required");
        assertThat(catchThrowableOfType(() -> inventory.writeOff(v, w, 0, "r", CTX), ValidationException.class).getCode()).isEqualTo("inventory.qty_positive");
    }

    @Test
    void write_off_records_current_reserved_in_journal() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 3)));

        inventory.writeOff(v, w, 2, "spill", CTX); // доступно 7 ≥ 2

        Map<String, Object> m = lastMovement(v, w);
        assertThat(m.get("qty_after")).isEqualTo(8);
        assertThat(m.get("reserved_after")).isEqualTo(3); // резерв не тронут, но зафиксирован
    }

    // ── Инвентаризация ───────────────────────────────────────────────────────────

    @Test
    void stocktake_sets_absolute_value_delta_is_difference() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);

        inventory.stocktake(v, w, 6, CTX);
        assertThat(qty(v, w)).isEqualTo(6);
        Map<String, Object> down = lastMovement(v, w);
        assertThat(down.get("type")).isEqualTo("STOCKTAKE");
        assertThat(down.get("qty_delta")).isEqualTo(-4);
        assertThat(down.get("qty_after")).isEqualTo(6);

        inventory.stocktake(v, w, 15, CTX);
        assertThat(qty(v, w)).isEqualTo(15);
        assertThat(lastMovement(v, w).get("qty_delta")).isEqualTo(9);
    }

    @Test
    void stocktake_below_reserved_is_rejected_naming_reserved() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 8)));

        ConflictException ex = catchThrowableOfType(() -> inventory.stocktake(v, w, 5, CTX), ConflictException.class);
        assertThat(ex.getCode()).isEqualTo("inventory.stocktake_below_reserved");
        assertThat(ex.getArgs()).containsExactly(8); // сообщение называет количество в резерве
        assertThat(qty(v, w)).isEqualTo(10);          // остаток не изменён
    }

    @Test
    void stocktake_creates_row_when_absent() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.stocktake(v, w, 12, CTX); // строки ещё нет
        assertThat(qty(v, w)).isEqualTo(12);
        assertThat(lastMovement(v, w).get("qty_delta")).isEqualTo(12);
    }

    // ── Перемещение ──────────────────────────────────────────────────────────────

    @Test
    void transfer_moves_between_warehouses_with_two_movements() {
        long v = seedVariant();
        long w1 = seedWarehouse();
        long w2 = seedWarehouse();
        inventory.receive(v, w1, 10);

        inventory.transfer(v, w1, w2, 4, CTX);

        assertThat(qty(v, w1)).isEqualTo(6);
        assertThat(qty(v, w2)).isEqualTo(4);
        Map<String, Object> out = lastMovement(v, w1);
        assertThat(out.get("type")).isEqualTo("TRANSFER_OUT");
        assertThat(out.get("qty_delta")).isEqualTo(-4);
        assertThat(out.get("qty_after")).isEqualTo(6);
        Map<String, Object> in = lastMovement(v, w2);
        assertThat(in.get("type")).isEqualTo("TRANSFER_IN");
        assertThat(in.get("qty_delta")).isEqualTo(4);
        assertThat(in.get("qty_after")).isEqualTo(4);
    }

    @Test
    void transfer_rejects_insufficient_source_and_same_warehouse() {
        long v = seedVariant();
        long w1 = seedWarehouse();
        long w2 = seedWarehouse();
        inventory.receive(v, w1, 3);
        catchThrowableOfType(() -> inventory.transfer(v, w1, w2, 5, CTX), ConflictException.class);
        assertThat(qty(v, w1)).isEqualTo(3);
        assertThat(catchThrowableOfType(() -> inventory.transfer(v, w1, w1, 1, CTX), ValidationException.class).getCode()).isEqualTo("inventory.transfer_same_warehouse");
    }

    // ── Публичное чтение ─────────────────────────────────────────────────────────

    @Test
    void available_reads_reflect_stock_and_reserved() {
        long v = seedVariant();
        long w1 = seedWarehouse();
        long w2 = seedWarehouse();
        inventory.receive(v, w1, 10);
        inventory.receive(v, w2, 5);
        inventory.reserve(seedRequest(), List.of(new ReserveItem(v, 3))); // резерв уйдёт на w1

        assertThat(inventory.getAvailable(v)).isEqualTo(12); // (10-3) + 5
        assertThat(inventory.isAvailable(v, 12)).isTrue();
        assertThat(inventory.isAvailable(v, 13)).isFalse();
        Map<Long, Integer> byWh = inventory.getAvailableByWarehouse(v);
        assertThat(byWh).containsEntry(w1, 7).containsEntry(w2, 5);
    }

    @Test
    void available_batch_is_one_query_and_zero_fills_missing() {
        long v1 = seedVariant();
        long v2 = seedVariant();
        long missing = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v1, w, 8);
        inventory.receive(v2, w, 2);

        Map<Long, Integer> batch = inventory.getAvailableBatch(List.of(v1, v2, missing));
        assertThat(batch).containsEntry(v1, 8).containsEntry(v2, 2).containsEntry(missing, 0);
    }

    // ── Низкий остаток ───────────────────────────────────────────────────────────

    @Test
    void low_stock_event_fires_after_commit_when_available_drops_below_threshold() {
        long v = seedVariant();
        long w = seedWarehouse();
        inventory.receive(v, w, 10);
        jdbcTemplate.update("update inventory set low_stock_threshold = 5 where variant_id = ? and warehouse_id = ?", v, w);
        lowStock.events.clear();

        inventory.writeOff(v, w, 4, "shrinkage", CTX); // доступно 6 ≥ 5 → без события
        assertThat(lowStock.events).isEmpty();

        inventory.writeOff(v, w, 3, "shrinkage", CTX); // доступно 3 < 5 → событие
        assertThat(lowStock.events).hasSize(1);
        LowStockEvent e = lowStock.events.get(0);
        assertThat(e.variantId()).isEqualTo(v);
        assertThat(e.warehouseId()).isEqualTo(w);
        assertThat(e.available()).isEqualTo(3);
        assertThat(e.threshold()).isEqualTo(5);
    }

    // ── Сходимость журнала ───────────────────────────────────────────────────────

    @Test
    void journal_sums_to_qty_after_mixed_ten_operations() {
        long v = seedVariant();
        long w1 = seedWarehouse();
        long w2 = seedWarehouse();
        long reqA = seedRequest();

        inventory.receive(v, w1, 100);                       // 1
        inventory.reserve(reqA, List.of(new ReserveItem(v, 10))); // 2 (резерв на w1)
        inventory.writeOff(v, w1, 5, "damage", CTX);         // 3
        inventory.receive(v, w1, 20);                        // 4
        inventory.transfer(v, w1, w2, 30, CTX);              // 5
        inventory.stocktake(v, w1, 80, CTX);                 // 6
        inventory.consume(reqA);                             // 7 (продажа 10 с w1)
        inventory.writeOff(v, w2, 10, "expired", CTX);       // 8
        inventory.receive(v, w2, 5);                         // 9
        inventory.transfer(v, w2, w1, 15, CTX);              // 10

        // Инвариант R-41: sum(qty_delta) == inventory.qty по каждому складу.
        assertThat(sumDelta(v, w1)).isEqualTo(qty(v, w1)).isEqualTo(85);
        assertThat(sumDelta(v, w2)).isEqualTo(qty(v, w2)).isEqualTo(10);
        assertThat(reserved(v, w1)).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private Map<String, Object> lastMovement(long v, long w) {
        return jdbcTemplate.queryForMap("select type, qty_delta, qty_after, reserved_delta, reserved_after, comment "
                + "from inventory_movements where variant_id = ? and warehouse_id = ? order by id desc limit 1", v, w);
    }

    private int sumDelta(long v, long w) {
        Integer s = jdbcTemplate.queryForObject("select coalesce(sum(qty_delta),0) from inventory_movements "
                + "where variant_id = ? and warehouse_id = ?", Integer.class, v, w);
        return s == null ? 0 : s;
    }

    private int qty(long v, long w) {
        return jdbcTemplate.queryForObject("select qty from inventory where variant_id = ? and warehouse_id = ?",
                Integer.class, v, w);
    }

    private int reserved(long v, long w) {
        return jdbcTemplate.queryForObject("select reserved_qty from inventory where variant_id = ? and warehouse_id = ?",
                Integer.class, v, w);
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

    private long seedWarehouse() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "insert into warehouses (code, is_active) values (?, true) returning id", Long.class, "WH-" + t);
    }

    private long seedRequest() {
        String t = UUID.randomUUID().toString().substring(0, 10);
        return jdbcTemplate.queryForObject("""
                        insert into product_requests (request_number, customer_name, customer_phone, source,
                                                      consent_given_at, policy_version)
                        values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id""",
                Long.class, "PR-" + t);
    }

    /** Ловит LowStockEvent после коммита — доказывает и факт события, и фазу AFTER_COMMIT. */
    static class LowStockCapture {
        final List<LowStockEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(LowStockEvent e) {
            events.add(e);
        }
    }

    @TestConfiguration
    static class TestListeners {
        @Bean
        LowStockCapture lowStockCapture() {
            return new LowStockCapture();
        }
    }
}
