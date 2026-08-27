package com.tzevabest.request.dto;

import com.tzevabest.request.domain.ProductRequestStatus;
import jakarta.validation.constraints.NotNull;

/** Запрос на смену статуса заявки. */
public record ProductRequestTransition(@NotNull ProductRequestStatus to, String comment) {
}
