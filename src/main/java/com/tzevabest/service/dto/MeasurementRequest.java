package com.tzevabest.service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Планирование замера: дата и время. Отложенное напоминание клиенту за сутки. */
public record MeasurementRequest(@NotNull Instant at) {
}
