package com.tzevabest.notification.dto;

/** Внутренний алерт о низком остатке. */
public record LowStockAlertModel(String sku, String warehouseCode, int available, int threshold)
        implements NotificationModel {
}
