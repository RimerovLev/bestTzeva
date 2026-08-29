package com.tzevabest.inventory.dto;

import java.time.Instant;

/** Активный резерв по варианту: какая заявка держит и до какого срока. */
public record ActiveReservationView(
        long reservationId,
        long requestId,
        String requestNumber,
        long warehouseId,
        int qty,
        Instant expiresAt) {
}
