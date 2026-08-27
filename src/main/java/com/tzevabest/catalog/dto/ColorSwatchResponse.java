package com.tzevabest.catalog.dto;

import com.tzevabest.catalog.domain.ColorSystem;

/** Цвет палитры для чтения (имя локализовано). */
public record ColorSwatchResponse(
        long id, String code, ColorSystem system, String hex, String name, String collection, boolean popular) {
}
