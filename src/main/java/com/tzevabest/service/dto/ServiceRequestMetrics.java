package com.tzevabest.service.dto;

import java.util.Map;

/** Агрегаты воронки: всего и по статусам. */
public record ServiceRequestMetrics(long total, Map<String, Long> byStatus) {
}
