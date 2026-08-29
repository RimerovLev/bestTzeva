package com.tzevabest.notification.dto;

import com.tzevabest.notification.domain.NotificationChannel;

/** Отрендеренное сообщение: канал, тема (для email) и тело (HTML для email, текст для SMS/WhatsApp). */
public record RenderedMessage(NotificationChannel channel, String subject, String body) {
}
