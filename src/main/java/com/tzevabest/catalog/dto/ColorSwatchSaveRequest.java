package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.ColorSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** Создание/обновление цвета палитры. name_translations — карта локаль→имя. */
public record ColorSwatchSaveRequest(
        @NotBlank String code,
        @NotNull ColorSystem system,
        @Pattern(regexp = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$", message = "must be a hex color like #RRGGBB") String hex,
        Map<String, String> nameTranslations,
        String collection,
        Boolean popular) {
}
