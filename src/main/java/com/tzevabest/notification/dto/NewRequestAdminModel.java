package com.tzevabest.notification.dto;

/** Внутреннее уведомление персоналу о новой заявке. */
public record NewRequestAdminModel(String requestNumber, String kind) implements NotificationModel {
}
