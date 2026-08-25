package com.tzevabest.service.domain;

/** Статус заявки на услугу. varchar + check ck_service_requests_status. */
public enum ServiceRequestStatus {
    NEW, CONTACTED, MEASUREMENT_SCHEDULED, MEASURED, QUOTED,
    ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED, LOST
}
