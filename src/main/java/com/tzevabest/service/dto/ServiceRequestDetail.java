package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.service.domain.WorkType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Карточка заявки на покраску с историей и внутренними комментариями. */
public record ServiceRequestDetail(
        long id, String requestNumber, String customerName, String customerPhone, String customerEmail,
        PropertyType propertyType, BigDecimal areaSqm, Integer roomsCount, BigDecimal ceilingHeightM,
        Set<WorkType> workTypes, String city, String address, LocalDate preferredDateFrom, LocalDate preferredDateTo,
        String comment, BigDecimal estimatedPriceMin, BigDecimal estimatedPriceMax, BigDecimal quotedPrice,
        ServiceRequestStatus status, Long assignedManagerId, String source, String locale,
        Instant measurementAt, Instant createdAt, List<HistoryView> history, List<CommentView> comments) {
}
