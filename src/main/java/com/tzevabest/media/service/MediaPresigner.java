package com.tzevabest.media.service;

import com.tzevabest.media.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Presigned-доступ к ПРИВАТНОМУ бакету (T-11.1): клиент грузит фото помещения напрямую по presigned
 * PUT, а выдача — только по presigned GET с коротким сроком. Персональные данные не выкладываются в
 * публичный бакет.
 */
@Service
@RequiredArgsConstructor
public class MediaPresigner {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final StorageProperties props;

    /** Presigned PUT в приватный бакет под ключ {@code keyPrefix/UUID.ext}. */
    public PresignedUpload presignUpload(String keyPrefix, String filename, String contentType) {
        String key = normalizePrefix(keyPrefix) + UUID.randomUUID() + extension(filename);
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.privateBucket())
                .key(key)
                .contentType(contentType)
                .build();
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_TTL)
                .putObjectRequest(put)
                .build()).url().toString();
        return new PresignedUpload(key, url);
    }

    /** Presigned GET из приватного бакета с коротким сроком. */
    public String presignDownload(String objectKey) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.privateBucket())
                .key(objectKey)
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_TTL)
                .getObjectRequest(get)
                .build()).url().toString();
    }

    /** Принадлежит ли ключ ожидаемому префиксу (защита от подстановки чужого ключа). */
    public boolean hasPrefix(String objectKey, String keyPrefix) {
        return objectKey != null && objectKey.startsWith(normalizePrefix(keyPrefix));
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "") + "/";
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }
}
