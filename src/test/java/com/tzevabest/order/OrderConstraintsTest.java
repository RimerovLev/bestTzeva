package com.tzevabest.order;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Уникальность номера заказа и ключа идемпотентности платежа. */
class OrderConstraintsTest extends IntegrationTest {

    @Test
    void order_number_is_unique() {
        String number = "TB-250825-0001";
        factory.createOrder(spec -> spec.orderNumber = number);

        assertThatThrownBy(() -> factory.createOrder(spec -> spec.orderNumber = number))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payment_idempotency_key_is_unique() {
        TestDataFactory.TestOrder order = factory.createOrder();
        String key = "idem-" + UUID.randomUUID();

        insertPayment(order.id(), key);

        assertThatThrownBy(() -> insertPayment(order.id(), key))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertPayment(long orderId, String idempotencyKey) {
        jdbcTemplate.update("""
                        insert into payments (order_id, provider, status, amount, idempotency_key)
                        values (?, 'MANUAL', 'PENDING', ?, ?)""",
                orderId, new BigDecimal("100.00"), idempotencyKey);
    }
}
