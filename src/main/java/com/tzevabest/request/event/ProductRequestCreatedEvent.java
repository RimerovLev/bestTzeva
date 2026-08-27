package com.tzevabest.request.event;

/**
 * Заявка на товар создана. Слушатель шлёт подтверждение клиенту и уведомление менеджерам.
 * Публикуется после коммита транзакции создания.
 */
public record ProductRequestCreatedEvent(long requestId, String requestNumber,
                                         String customerPhone, String customerEmail, String locale) {
}
