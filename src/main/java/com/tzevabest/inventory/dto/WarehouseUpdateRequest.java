package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Редактирование склада (активность меняется отдельным эндпоинтом). */
public record WarehouseUpdateRequest(
        @NotBlank @Size(max = 64) String code,
        Map<String, String> nameTranslations,
        String address,
        Boolean pickupPoint,
        Integer sortOrder) {
}
