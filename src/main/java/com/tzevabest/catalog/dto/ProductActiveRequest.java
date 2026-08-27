package com.tzevabest.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Точечное изменение активности товара. */
public record ProductActiveRequest(@NotNull Boolean active) {
}
