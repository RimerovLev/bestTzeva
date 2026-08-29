package com.tzevabest.notification.dto;

/** Модель для уведомлений по заявке на товар (received/contacted/reserved/expired). */
public record ProductRequestModel(String requestNumber) implements NotificationModel {
}
