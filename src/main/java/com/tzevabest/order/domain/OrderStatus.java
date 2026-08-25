package com.tzevabest.order.domain;

/** Статус заказа. varchar + check ck_orders_status. */
public enum OrderStatus {
    NEW, AWAITING_PAYMENT, PAID, PROCESSING, READY_FOR_PICKUP, SHIPPED, COMPLETED, CANCELLED, REFUNDED
}
