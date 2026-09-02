package com.tzevabest.service.dto;

import com.tzevabest.service.domain.ServiceRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Переход статуса заявки. */
public record TransitionRequest(@NotNull ServiceRequestStatus to, @Size(max = 500) String comment) {
}
