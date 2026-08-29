package com.tzevabest.notification.dto;

/** Напоминание о замере. */
public record MeasurementReminderModel(String requestNumber, String dateTime) implements NotificationModel {
}
