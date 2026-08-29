package com.tzevabest.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Создание склада. code уникален. */
public record WarehouseCreateRequest(
        @NotBlank @Size(max = 64) String code,
        Map<String, String> nameTranslations,
        String address,
        Boolean pickupPoint,
        Integer sortOrder) {
}
