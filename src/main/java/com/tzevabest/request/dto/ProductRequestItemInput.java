package com.tzevabest.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** Позиция заявки: вариант и количество. Цена берётся с сервера, а не из запроса. */
public record ProductRequestItemInput(@NotNull Long variantId, @NotNull @Positive @Max(999) Integer qty) {
}
