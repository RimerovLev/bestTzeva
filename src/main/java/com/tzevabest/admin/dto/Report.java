package com.tzevabest.admin.dto;

import java.util.List;

/** Табличный отчёт: локализованный заголовок, заголовки колонок и строки. */
public record Report(String title, List<String> headers, List<List<Object>> rows) {
}
