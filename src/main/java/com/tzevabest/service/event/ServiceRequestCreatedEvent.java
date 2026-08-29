package com.tzevabest.service.event;

/**
 * Заявка на покраску создана. Слушатель шлёт подтверждение клиенту и уведомление менеджерам.
 * Публикуется после коммита транзакции создания.
 */
public record ServiceRequestCreatedEvent(long requestId, String requestNumber,
                                         String customerPhone, String customerEmail, String locale) {
}
