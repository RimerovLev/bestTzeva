package com.tzevabest.notification.dto;

/** Модель с одной ссылкой (сброс пароля, подтверждение email). */
public record LinkModel(String link) implements NotificationModel {
}
