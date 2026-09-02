package com.tzevabest.media.service;

/**
 * Хранилище бинарных медиа (изображения/видео товаров). Абстракция над S3-совместимым бэкендом,
 * чтобы модуль каталога не знал про S3 SDK.
 */
public interface MediaStorage {

    /**
     * Кладёт объект и возвращает его ключ и публичную ссылку.
     *
     * @param keyPrefix   логический префикс (напр. {@code products/42}); ключ = префикс/UUID.ext
     * @param filename    исходное имя файла (для расширения)
     * @param contentType MIME-тип
     * @param data        содержимое
     */
    StoredObject put(String keyPrefix, String filename, String contentType, byte[] data);

    /** Удаляет объект по ключу. Идемпотентно (отсутствие объекта — не ошибка). */
    void delete(String objectKey);

    /** Публичная ссылка на объект по ключу. */
    String publicUrl(String objectKey);

    /**
     * Кладёт серверный объект (напр. сгенерированный PDF) в ПРИВАТНЫЙ бакет и возвращает ключ.
     * Публичной ссылки нет — выдача через presigned GET.
     */
    String putPrivate(String keyPrefix, String filename, String contentType, byte[] data);
}
