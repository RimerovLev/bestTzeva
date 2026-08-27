package com.tzevabest.inventory.dto;

/** Позиция для резервирования: вариант и количество. */
public record ReserveItem(long variantId, int qty) {
}
