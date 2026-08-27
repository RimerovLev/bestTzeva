package com.tzevabest.request.dto;

import java.util.Map;

/** Метрики воронки заявок: количество по статусам, доля потерянных по причинам, конверсия. */
public record ProductRequestMetrics(
        Map<String, Long> byStatus,
        Map<String, Long> lostByReason,
        long total,
        double completionRate) {
}
