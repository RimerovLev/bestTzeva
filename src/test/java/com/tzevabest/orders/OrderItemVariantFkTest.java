package com.tzevabest.orders;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory.TestOrder;
import com.tzevabest.support.TestDataFactory.TestProduct;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-16: вариант товара, по которому есть позиция заказа, нельзя удалить физически
 * (FK order_items.variant_id — on delete restrict).
 */
class OrderItemVariantFkTest extends IntegrationTest {

    @Test
    void deleting_variant_with_order_item_is_rejected() {
        TestProduct product = factory.createProduct();
        long variantId = jdbcTemplate.queryForObject(
                "select id from product_variants where product_id = ?", Long.class, product.id());
        TestOrder order = factory.createOrder();

        jdbcTemplate.update("""
                        insert into order_items
                            (order_id, variant_id, product_name_snapshot, sku_snapshot,
                             unit_price, qty, line_total)
                        values (?, ?, ?, ?, ?, ?, ?)""",
                order.id(), variantId, "Test paint", product.sku(),
                new BigDecimal("99.90"), 1, new BigDecimal("99.90"));

        // вариант участвовал в заказе — база не даёт его удалить
        assertThatThrownBy(() -> jdbcTemplate.update("delete from product_variants where id = ?", variantId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
