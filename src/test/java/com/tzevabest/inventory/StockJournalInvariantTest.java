package com.tzevabest.inventory;

import com.tzevabest.inventory.dto.MovementContext;
import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.service.InventoryService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-14.3 (следствие R-41): после произвольной последовательности из 20 операций журнал сходится —
 * sum(qty_delta) == inventory.qty и sum(reserved_delta) == inventory.reserved_qty.
 */
class StockJournalInvariantTest extends IntegrationTest {

    @Autowired
    private InventoryService inventory;

    @Test
    void journal_sums_match_inventory_after_twenty_operations() {
        long variant = seedVariant();
        long wh1 = seedWarehouse();
        long wh2 = seedWarehouse();
        MovementContext ctx = MovementContext.manual(null);

        // 20 операций: приход, резерв, продажа, снятие, списание, инвентаризация, перемещение.
        inventory.receive(variant, wh1, 100);                 // 1
        inventory.receive(variant, wh2, 50);                  // 2
        long r1 = seedRequest();
        inventory.reserve(r1, List.of(new ReserveItem(variant, 10))); // 3
        long r2 = seedRequest();
        inventory.reserve(r2, List.of(new ReserveItem(variant, 20))); // 4
        inventory.consume(r1);                                // 5
        inventory.release(r2);                                // 6
        inventory.writeOff(variant, wh1, 5, "damage", ctx);   // 7
        inventory.receive(variant, wh1, 30);                  // 8
        long r3 = seedRequest();
        inventory.reserve(r3, List.of(new ReserveItem(variant, 15))); // 9
        inventory.stocktake(variant, wh2, 45, ctx);           // 10
        inventory.transfer(variant, wh1, wh2, 10, ctx);       // 11
        inventory.receive(variant, wh2, 25);                  // 12
        inventory.writeOff(variant, wh2, 3, "sample", ctx);   // 13
        long r4 = seedRequest();
        inventory.reserve(r4, List.of(new ReserveItem(variant, 5)));  // 14
        inventory.consume(r3);                                // 15
        inventory.receive(variant, wh1, 40);                  // 16
        inventory.transfer(variant, wh2, wh1, 5, ctx);        // 17
        inventory.stocktake(variant, wh1, 90, ctx);           // 18
        long r5 = seedRequest();
        inventory.reserve(r5, List.of(new ReserveItem(variant, 8)));  // 19
        inventory.release(r5);                                // 20

        for (long wh : List.of(wh1, wh2)) {
            Integer sumDelta = jdbcTemplate.queryForObject(
                    "select coalesce(sum(qty_delta),0) from inventory_movements where variant_id=? and warehouse_id=?",
                    Integer.class, variant, wh);
            Integer sumReserved = jdbcTemplate.queryForObject(
                    "select coalesce(sum(reserved_delta),0) from inventory_movements where variant_id=? and warehouse_id=?",
                    Integer.class, variant, wh);
            Integer qty = jdbcTemplate.queryForObject(
                    "select qty from inventory where variant_id=? and warehouse_id=?", Integer.class, variant, wh);
            Integer reserved = jdbcTemplate.queryForObject(
                    "select reserved_qty from inventory where variant_id=? and warehouse_id=?", Integer.class, variant, wh);
            assertThat(sumDelta).as("sum(qty_delta) == qty @wh%d", wh).isEqualTo(qty);
            assertThat(sumReserved).as("sum(reserved_delta) == reserved_qty @wh%d", wh).isEqualTo(reserved);
        }
    }

    private long seedVariant() {
        String t = uid();
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long product = jdbcTemplate.queryForObject("insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active) "
                + "values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id", Long.class, cat, brand, "P-" + t);
        return jdbcTemplate.queryForObject("insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active) "
                + "values (?, ?, 'RAL1', 1.0, 100.00, true) returning id", Long.class, product, "V-" + t);
    }

    private long seedWarehouse() {
        return jdbcTemplate.queryForObject("insert into warehouses (code, is_active) values (?, true) returning id",
                Long.class, "WH-" + uid());
    }

    private long seedRequest() {
        return jdbcTemplate.queryForObject("insert into product_requests "
                + "(request_number, customer_name, customer_phone, source, consent_given_at, policy_version) "
                + "values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id",
                Long.class, "PR-" + uid());
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 10);
    }
}
