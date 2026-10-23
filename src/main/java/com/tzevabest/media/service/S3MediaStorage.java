package com.tzevabest.media.service;

import com.tzevabest.media.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Реализация {@link MediaStorage} поверх S3-совместимого хранилища (MinIO/S3) в публичном бакете. */
@Service
@Slf4j
@RequiredArgsConstructor
public class S3MediaStorage implements MediaStorage {

    private final S3Client s3;
    private final StorageProperties props;

    @Override
    public StoredObject put(String keyPrefix, String filename, String contentType, byte[] data) {
        String key = normalizePrefix(keyPrefix) + UUID.randomUUID() + extensionFor(contentType, filename);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        return new StoredObject(key, publicUrl(key));
    }

    @Override
    public String putPrivate(String keyPrefix, String filename, String contentType, byte[] data) {
        String key = normalizePrefix(keyPrefix) + UUID.randomUUID() + extensionFor(contentType, filename);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.privateBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        return key;
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .build());
    }

    @Override
    public void deletePrivate(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.privateBucket())
                .key(objectKey)
                .build());
    }

    @Override
    public Optional<StoredObjectInfo> headPrivate(String objectKey) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(props.privateBucket())
                    .key(objectKey)
                    .build());
            long length = head.contentLength() == null ? 0 : head.contentLength();
            return Optional.of(new StoredObjectInfo(length, head.contentType()));
        } catch (S3Exception e) {
            return Optional.empty(); // объекта нет (404/NoSuchKey) — нечего проверять
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        // path-style: <base>/<bucket>/<key>
        return props.resolvedPublicBase() + "/" + props.bucket() + "/" + objectKey;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String p = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return p + "/";
    }

    /** Расширение по РАСПОЗНАННОМУ типу (аудит п.9), с откатом на расширение из имени клиента. */
    private static String extensionFor(String contentType, String filename) {
        if (contentType != null) {
            switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg": return ".jpg";
                case "image/png": return ".png";
                case "image/webp": return ".webp";
                case "image/gif": return ".gif";
                case "image/heic": return ".heic";
                case "image/avif": return ".avif";
                case "video/mp4": return ".mp4";
                case "video/webm": return ".webm";
                case "application/pdf": return ".pdf";
                default: break;
            }
        }
        return extension(filename);
    }

    /** Расширение из имени файла (с точкой), в нижнем регистре; пусто, если нет. */
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
