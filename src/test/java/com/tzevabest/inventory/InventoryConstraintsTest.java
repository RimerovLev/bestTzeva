package com.tzevabest.inventory;

import com.tzevabest.inventory.domain.Inventory;
import com.tzevabest.inventory.domain.Warehouse;
import com.tzevabest.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Check-ограничения остатков (не уйти в минус и не зарезервировать больше наличия),
 * представление доступного количества и optimistic lock через {@code @Version}.
 */
class InventoryConstraintsTest extends IntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void initTx() {
        tx = new TransactionTemplate(txManager);
    }

    @Test
    void negative_qty_is_rejected() {
        long variantId = newVariant();
        long warehouseId = newWarehouse();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, ?)",
                variantId, warehouseId, -1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reserved_greater_than_qty_is_rejected() {
        long variantId = newVariant();
        long warehouseId = newWarehouse();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, ?)",
                variantId, warehouseId, 5, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_raising_reserved_over_qty_is_rejected() {
        long variantId = newVariant();
        long warehouseId = newWarehouse();
        jdbcTemplate.update(
                "insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, ?)",
                variantId, warehouseId, 10, 3);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update inventory set reserved_qty = 20 where variant_id = ? and warehouse_id = ?",
                variantId, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void available_qty_view_returns_qty_minus_reserved() {
        long variantId = newVariant();
        long warehouseId = newWarehouse();
        jdbcTemplate.update(
                "insert into inventory (variant_id, warehouse_id, qty, reserved_qty) values (?, ?, ?, ?)",
                variantId, warehouseId, 10, 3);

        Integer perWarehouse = jdbcTemplate.queryForObject(
                "select available_qty from v_inventory_available where variant_id = ? and warehouse_id = ?",
                Integer.class, variantId, warehouseId);
        Integer perVariant = jdbcTemplate.queryForObject(
                "select available_qty from v_variant_available where variant_id = ?",
                Integer.class, variantId);

        assertThat(perWarehouse).isEqualTo(7);
        assertThat(perVariant).isEqualTo(7);
    }

    @Test
    void version_increments_on_update() {
        long variantId = newVariant();

        Long inventoryId = tx.execute(status -> {
            Warehouse warehouse = new Warehouse();
            warehouse.setCode("WH-" + token());
            em.persist(warehouse);

            Inventory inventory = new Inventory();
            inventory.setVariantId(variantId);
            inventory.setWarehouse(warehouse);
            inventory.setQty(10);
            inventory.setReservedQty(0);
            em.persist(inventory);
            em.flush();

            assertThat(inventory.getVersion()).isZero();
            return inventory.getId();
        });

        tx.executeWithoutResult(status -> {
            Inventory inventory = em.find(Inventory.class, inventoryId);
            inventory.setQty(15);
            em.flush();
            assertThat(inventory.getVersion()).isEqualTo(1L);
        });
    }

    private long newVariant() {
        var product = factory.createProduct();
        return jdbcTemplate.queryForObject(
                "select id from product_variants where product_id = ?", Long.class, product.id());
    }

    private long newWarehouse() {
        return jdbcTemplate.queryForObject(
                "insert into warehouses (code, is_active) values (?, true) returning id",
                Long.class, "WH-" + token());
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
