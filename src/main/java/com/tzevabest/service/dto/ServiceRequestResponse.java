package com.tzevabest.service.dto;

import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.util.Set;

/** Ответ на создание заявки на покраску. Итоговая цена определяется после замера. */
public record ServiceRequestResponse(
        long id,
        String requestNumber,
        ServiceRequestStatus status,
        BigDecimal estimatedPriceMin,
        BigDecimal estimatedPriceMax,
        Set<WorkType> workTypes,
        int photoCount) {
}
