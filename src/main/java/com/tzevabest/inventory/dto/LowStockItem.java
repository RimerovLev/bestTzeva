package com.tzevabest.inventory.dto;

/** Позиция ниже порога. criticality = available / threshold (меньше — критичнее). */
public record LowStockItem(
        long variantId,
        String sku,
        String productName,
        long warehouseId,
        String warehouseCode,
        int qty,
        int reservedQty,
        int available,
        int lowStockThreshold,
        double criticality) {
}
