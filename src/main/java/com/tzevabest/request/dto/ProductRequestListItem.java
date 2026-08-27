package com.tzevabest.request.dto;

import com.tzevabest.request.domain.ProductRequestStatus;

import java.time.Instant;

/** Строка списка заявок в админке. */
public record ProductRequestListItem(
        long id,
        String requestNumber,
        ProductRequestStatus status,
        String customerName,
        String customerPhone,
        Long assignedManagerId,
        int itemCount,
        Instant createdAt) {
}
