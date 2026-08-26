package com.tzevabest.catalog;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory.TestProduct;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-30: у товара не более одного главного изображения (частичный уникальный индекс по is_primary).
 */
class ProductMediaPrimaryTest extends IntegrationTest {

    @Test
    void second_primary_image_for_same_product_is_rejected() {
        TestProduct product = factory.createProduct();
        insertMedia(product.id(), true);

        assertThatThrownBy(() -> insertMedia(product.id(), true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multiple_non_primary_images_are_allowed() {
        TestProduct product = factory.createProduct();
        insertMedia(product.id(), false);
        assertThatCode(() -> insertMedia(product.id(), false)).doesNotThrowAnyException();
    }

    private void insertMedia(long productId, boolean primary) {
        jdbcTemplate.update("""
                        insert into product_media (product_id, url, type, is_primary)
                        values (?, 'https://x.test/i.png', 'IMAGE', ?)""",
                productId, primary);
    }
}
