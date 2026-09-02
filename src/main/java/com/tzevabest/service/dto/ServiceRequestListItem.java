package com.tzevabest.service.dto;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.ServiceRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Строка админ-списка заявок на покраску. */
public record ServiceRequestListItem(
        long id, String requestNumber, String customerName, String customerPhone,
        PropertyType propertyType, String city, BigDecimal areaSqm, ServiceRequestStatus status,
        Long assignedManagerId, String source, Instant createdAt) {
}
