package com.tzevabest.media.service;

/** Метаданные объекта в хранилище (размер в байтах, MIME-тип) — для проверок при привязке (аудит п.8). */
public record StoredObjectInfo(long contentLength, String contentType) {
}
