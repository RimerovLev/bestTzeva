package com.tzevabest.inventory.domain;

/** Тип складского движения. Хранится как varchar + check ck_inventory_movements_type. */
public enum MovementType {
    RECEIPT, SALE, RESERVATION, RESERVATION_RELEASE,
    WRITE_OFF, STOCKTAKE, TRANSFER_IN, TRANSFER_OUT
}
