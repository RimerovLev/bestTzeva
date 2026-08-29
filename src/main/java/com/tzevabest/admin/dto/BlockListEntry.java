package com.tzevabest.admin.dto;

import java.time.Instant;

/** Запись блок-листа (IP или телефон). */
public record BlockListEntry(long id, String kind, String value, String reason, Instant createdAt) {
}
