package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Перемещение между складами. */
public record TransferRequest(
        @NotNull Long variantId,
        @NotNull Long fromWarehouseId,
        @NotNull Long toWarehouseId,
        @NotNull @Positive @Max(1_000_000) Integer qty,
        String comment) {
}
