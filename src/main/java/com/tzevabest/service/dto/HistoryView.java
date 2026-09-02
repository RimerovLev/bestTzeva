package com.tzevabest.service.dto;

import java.time.Instant;

/** Строка истории смены статуса. */
public record HistoryView(String fromStatus, String toStatus, Long changedBy, String comment, Instant createdAt) {
}
