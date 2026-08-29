package com.tzevabest.notification.dto;

/** Котировка по заявке на покраску. */
public record ServiceRequestQuotedModel(String requestNumber, String price) implements NotificationModel {
}
