package com.tzevabest.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Публичная заявка на товар. consent обязателен (согласие на обработку данных). website — honeypot:
 * скрытое поле, которое заполняют боты; при непустом значении заявка отклоняется.
 */
public record ProductRequestCreate(
        @NotBlank String customerName,
        @NotBlank String customerPhone,
        String customerEmail,
        String comment,
        String preferredContactTime,
        boolean consent,
        String website,
        @NotEmpty @Size(max = 50) @Valid List<ProductRequestItemInput> items) {
}
