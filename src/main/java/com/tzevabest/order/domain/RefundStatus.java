package com.tzevabest.order.domain;

/** Статус возврата. varchar + check ck_payment_refunds_status. */
public enum RefundStatus {
    PENDING, SUCCEEDED, FAILED
}
