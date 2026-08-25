package com.tzevabest.order.domain;

/** Платёжный статус заказа. varchar + check ck_orders_payment_status. */
public enum OrderPaymentStatus {
    PENDING, AUTHORIZED, PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED
}
