package com.tzevabest.request.dto;

import com.tzevabest.request.domain.ProductRequestStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ответ на создание заявки. indicativeTotal — ориентировочная сумма (справочно): оплаты и доставки в
 * заявке нет.
 */
public record ProductRequestResponse(
        long id,
        String requestNumber,
        ProductRequestStatus status,
        BigDecimal indicativeTotal,
        List<ProductRequestItemView> items) {
}
