package com.tzevabest.media.service;

/** Результат сохранения медиа: ключ объекта в бакете и публичная ссылка. */
public record StoredObject(String objectKey, String url) {
}
