package com.tzevabest.orders;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory.TestOrder;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-17: пара (provider, provider_transaction_id) уникальна — повторный callback от эквайринга
 * не создаёт второй платёж, а натыкается на ограничение.
 */
class PaymentProviderTxnUniqueTest extends IntegrationTest {

    @Test
    void duplicate_provider_transaction_id_is_rejected() {
        TestOrder order = factory.createOrder();
        insertPayment(order.id(), "TRANZILA", "TXN-123", "key-1");

        // повторный callback с той же (provider, transaction_id) отклоняется
        assertThatThrownBy(() -> insertPayment(order.id(), "TRANZILA", "TXN-123", "key-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void null_transaction_id_is_not_constrained() {
        TestOrder order = factory.createOrder();
        insertPayment(order.id(), "TRANZILA", null, "key-a");
        // несколько платежей без внешнего id (ещё не подтверждены) допускаются
        assertThatCode(() -> insertPayment(order.id(), "TRANZILA", null, "key-b"))
                .doesNotThrowAnyException();
    }

    private void insertPayment(long orderId, String provider, String txnId, String idempotencyKey) {
        jdbcTemplate.update("""
                        insert into payments
                            (order_id, provider, provider_transaction_id, status, amount, idempotency_key)
                        values (?, ?, ?, 'PENDING', ?, ?)""",
                orderId, provider, txnId, new BigDecimal("99.90"), idempotencyKey);
    }
}
