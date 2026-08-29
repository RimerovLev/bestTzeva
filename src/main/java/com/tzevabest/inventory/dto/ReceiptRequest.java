package com.tzevabest.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Массовый приход на склад по списку SKU с общим комментарием. */
public record ReceiptRequest(
        @NotNull Long warehouseId,
        String comment,
        @NotEmpty @Size(max = 1000) @Valid List<ReceiptLine> lines) {
}
