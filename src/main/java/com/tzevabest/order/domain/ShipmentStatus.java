package com.tzevabest.order.domain;

/** Статус отгрузки. varchar + check ck_shipments_status. */
public enum ShipmentStatus {
    PENDING, SHIPPED, IN_TRANSIT, DELIVERED, RETURNED, CANCELLED
}
