package com.tzevabest.inventory.dto;

/**
 * Строка админ-списка остатков. Точные числа отдаём (ограничение R-47 — только для публичного API).
 */
public record InventoryListItem(
        long variantId,
        String sku,
        String productName,
        long warehouseId,
        String warehouseCode,
        Long categoryId,
        Long brandId,
        int qty,
        int reservedQty,
        int available,
        Integer lowStockThreshold,
        InventoryStatus status) {
}
