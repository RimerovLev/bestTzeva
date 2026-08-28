package com.tzevabest.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки S3-совместимого хранилища медиа (MinIO в dev/CI, S3 в проде).
 *
 * @param endpoint        адрес S3 (для MinIO — http://host:9000; для реального S3 можно оставить пустым)
 * @param accessKey       ключ доступа
 * @param secretKey       секрет
 * @param bucket          публичный бакет для медиа каталога
 * @param privateBucket   приватный бакет (фото заявок — персональные данные, только presigned GET)
 * @param region          регион
 * @param pathStyleAccess path-style URL (обязателен для MinIO и большинства S3-совместимых)
 * @param publicBaseUrl   базовый URL для сборки публичных ссылок; пусто → берётся endpoint
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record StorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String privateBucket,
        String region,
        boolean pathStyleAccess,
        String publicBaseUrl) {

    /** База для публичных ссылок: явный publicBaseUrl, иначе endpoint. Без хвостового слэша. */
    public String resolvedPublicBase() {
        String base = (publicBaseUrl != null && !publicBaseUrl.isBlank()) ? publicBaseUrl : endpoint;
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
