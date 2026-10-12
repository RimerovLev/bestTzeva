package com.tzevabest.service.dto;

import com.tzevabest.service.domain.ServiceMediaType;

import java.time.Instant;

/**
 * Фото/медиа клиента из заявки для персонала. {@code url} — presigned GET из приватного бакета
 * с коротким сроком жизни (минуты): ссылку не хранят и не пересылают, а запрашивают заново.
 */
public record ServiceRequestMediaView(Long id, ServiceMediaType type, Instant uploadedAt, String url) {
}
