package com.tzevabest.inventory.dto;

import java.time.Instant;

/** Строка журнала движений для админ-выдачи. */
public record MovementView(
        long id,
        String type,
        int qtyDelta,
        int qtyAfter,
        int reservedDelta,
        Integer reservedAfter,
        String referenceType,
        Long referenceId,
        String comment,
        Long createdBy,
        Instant createdAt) {
}
