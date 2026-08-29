package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.NotNull;

/** Смена активности склада. Деактивация запрещена при ненулевых остатках/активных резервах. */
public record WarehouseActiveRequest(@NotNull Boolean active) {
}
