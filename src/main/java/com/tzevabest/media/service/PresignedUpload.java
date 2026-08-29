package com.tzevabest.media.service;

/** Presigned-загрузка: ключ объекта и URL для прямого PUT клиентом в приватный бакет. */
public record PresignedUpload(String objectKey, String uploadUrl) {
}
