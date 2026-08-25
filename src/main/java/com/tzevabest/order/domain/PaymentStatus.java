package com.tzevabest.order.domain;

/** Статус платежа. varchar + check ck_payments_status. */
public enum PaymentStatus {
    PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, PARTIALLY_REFUNDED
}
