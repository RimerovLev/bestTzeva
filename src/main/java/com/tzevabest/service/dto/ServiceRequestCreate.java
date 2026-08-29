package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Публичная заявка на покраску. consent обязателен. Диапазоны площади/высоты/комнат ограничены, список
 * видов работ и фото — по размеру (R-44: публичная форма без верхних границ недопустима). website —
 * honeypot (усилит T-11.1.1). photoKeys — ключи из presigned-загрузки в приватный бакет.
 */
public record ServiceRequestCreate(
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Size(max = 32) String customerPhone,
        @Email @Size(max = 255) String customerEmail,
        @NotNull PropertyType propertyType,
        @DecimalMin("1.0") @DecimalMax("100000.0") BigDecimal areaSqm,
        @Min(0) @Max(1000) Integer roomsCount,
        @DecimalMin("1.5") @DecimalMax("20.0") BigDecimal ceilingHeightM,
        @Size(max = 6) Set<WorkType> workTypes,
        @Size(max = 120) String city,
        @Size(max = 2000) String address,
        LocalDate preferredDateFrom,
        LocalDate preferredDateTo,
        @Size(max = 2000) String comment,
        boolean consent,
        String website,
        @Size(max = 10) List<String> photoKeys) {
}
