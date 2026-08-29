package com.tzevabest.inventory.dto;

import com.tzevabest.common.dto.PageResponse;

import java.util.List;

/** Журнал движений варианта (страница) плюс активные резервы, держащие остаток. */
public record VariantMovements(
        PageResponse<MovementView> movements,
        List<ActiveReservationView> activeReservations) {
}
