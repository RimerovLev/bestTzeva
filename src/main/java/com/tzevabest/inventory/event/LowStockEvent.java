package com.tzevabest.inventory.event;

/**
 * Доступный остаток варианта на складе упал ниже порога {@code low_stock_threshold}. Публикуется в
 * транзакции операции движения; слушатель реагирует после коммита (AFTER_COMMIT).
 *
 * @param variantId   вариант
 * @param warehouseId склад
 * @param available   доступно после операции ({@code qty - reserved_qty})
 * @param threshold   порог, ниже которого сработал сигнал
 */
public record LowStockEvent(long variantId, long warehouseId, int available, int threshold) {
}
