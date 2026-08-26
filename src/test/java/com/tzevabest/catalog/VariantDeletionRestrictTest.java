package com.tzevabest.catalog;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-16: вариант, по которому есть позиция заказа, нельзя удалить физически — FK order_items.variant_id
 * переведён на on delete restrict (было set null, что молча теряло связь позиции с каталогом).
 */
class VariantDeletionRestrictTest extends IntegrationTest {

    @Autowired
    private TestDataFactory factory;

    @Test
    void deleting_variant_with_order_history_is_rejected() {
        TestDataFactory.TestProduct product = factory.createProduct();
        long variantId = jdbcTemplate.queryForObject(
                "select id from product_variants where product_id = ?", Long.class, product.id());
        TestDataFactory.TestOrder order = factory.createOrder();

        jdbcTemplate.update("""
                        insert into order_items
                            (order_id, variant_id, product_name_snapshot, sku_snapshot,
                             unit_price, qty, line_total)
                        values (?, ?, ?, ?, ?, ?, ?)""",
                order.id(), variantId, "Paint", product.sku(),
                new BigDecimal("99.90"), 1, new BigDecimal("99.90"));

        // удаление варианта с историей заказов отклоняется базой (restrict)
        assertThatThrownBy(() -> jdbcTemplate.update("delete from product_variants where id = ?", variantId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
