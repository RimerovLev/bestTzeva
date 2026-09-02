package com.tzevabest.catalog.dto;

/** Ошибка строки импорта: номер строки (с 1, без заголовка) и причина. */
public record ImportError(int row, String reason) {
}
