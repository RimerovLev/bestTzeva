package com.tzevabest.service.dto;

/** Ответ presigned-загрузки: ключ объекта (его клиент вернёт в заявке) и URL для PUT. */
public record PresignedUploadView(String objectKey, String uploadUrl) {
}
