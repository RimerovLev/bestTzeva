package com.tzevabest.admin.dto;

/** Строка топа: ключ, подпись и суммарное запрошенное количество. */
public record TopItem(String key, String label, long quantity) {
}
