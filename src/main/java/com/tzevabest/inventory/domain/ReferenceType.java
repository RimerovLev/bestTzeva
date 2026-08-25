package com.tzevabest.inventory.domain;

/** Источник движения. Хранится как varchar + check ck_inventory_movements_reference_type. */
public enum ReferenceType {
    ORDER, MANUAL, IMPORT
}
