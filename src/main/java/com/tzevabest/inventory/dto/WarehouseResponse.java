package com.tzevabest.inventory.dto;

import java.util.Map;

/** Ответ по складу. */
public record WarehouseResponse(
        long id,
        String code,
        Map<String, String> nameTranslations,
        String address,
        boolean pickupPoint,
        boolean active,
        int sortOrder) {
}
