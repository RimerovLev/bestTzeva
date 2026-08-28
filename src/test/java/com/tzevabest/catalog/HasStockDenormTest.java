package com.tzevabest.catalog;

import com.tzevabest.inventory.dto.ReserveItem;
import com.tzevabest.inventory.service.InventoryService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-40/R-49: has_stock пересчитывается при изменении inventory (не только product_variants); массовый
 * приход одним UPDATE пересчитывает все затронутые товары.
 */
class HasStockDenormTest extends IntegrationTest {

    @Autowired
    private InventoryService inventory;

    @Test
    void inventory_changes_recompute_has_stock() {
        long variant = seedVariant();
        long warehouse = seedWarehouse();
        long product = productOf(variant);
        assertThat(hasStock(product)).isFalse(); // остатка нет

        // приход без касания product_variants → has_stock истинно
        jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, 1, 0)",
                variant, warehouse);
        assertThat(hasStock(product)).isTrue();

        // резерв последней единицы → доступного нет → has_stock ложно
        long request = seedRequest();
        inventory.reserve(request, List.of(new ReserveItem(variant, 1)));
        assertThat(hasStock(product)).isFalse();

        // снятие резерва → снова истинно
        inventory.release(request);
        assertThat(hasStock(product)).isTrue();
    }

    @Test
    void batch_inventory_update_recomputes_all_affected_products() {
        long warehouse = seedWarehouse();
        List<Long> products = new ArrayList<>();
        List<Long> variants = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long v = seedVariant();
            variants.add(v);
            products.add(productOf(v));
            jdbcTemplate.update("insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, 0, 0)",
                    v, warehouse);
        }
        products.forEach(p -> assertThat(hasStock(p)).isFalse());

        // один UPDATE по 50 вариантам (statement-level триггер → один пересчёт на товар)
        jdbcTemplate.update("update inventory set qty = qty + 10 where warehouse_id = ?", warehouse);
        products.forEach(p -> assertThat(hasStock(p)).isTrue());
    }

    private long seedVariant() {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brandId = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long productId = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""",
                Long.class, categoryId, brandId, "P-" + t);
        return jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, 'RAL1', 1.0, 100.00, true) returning id""", Long.class, productId, "V-" + t);
    }

    private long seedWarehouse() {
        return jdbcTemplate.queryForObject("insert into warehouses (code, is_active) values (?, true) returning id",
                Long.class, "WH-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private long seedRequest() {
        return jdbcTemplate.queryForObject("""
                insert into product_requests (request_number, customer_name, customer_phone, source, consent_given_at, policy_version)
                values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01') returning id""",
                Long.class, "PR-" + UUID.randomUUID().toString().substring(0, 10));
    }

    private long productOf(long variantId) {
        return jdbcTemplate.queryForObject("select product_id from product_variants where id = ?", Long.class, variantId);
    }

    private boolean hasStock(long productId) {
        return jdbcTemplate.queryForObject("select has_stock from products where id = ?", Boolean.class, productId);
    }
}
