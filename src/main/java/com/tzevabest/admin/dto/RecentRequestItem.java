package com.tzevabest.admin.dto;

import java.time.Instant;

/** Последняя заявка (обоих типов). */
public record RecentRequestItem(String type, String number, String customerName, String status, Instant createdAt) {
}
