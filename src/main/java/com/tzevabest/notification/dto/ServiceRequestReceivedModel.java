package com.tzevabest.notification.dto;

/** Заявка на покраску принята. */
public record ServiceRequestReceivedModel(String requestNumber) implements NotificationModel {
}
