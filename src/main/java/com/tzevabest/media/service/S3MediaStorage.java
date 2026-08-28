package com.tzevabest.media.service;

import com.tzevabest.media.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Locale;
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
        String key = normalizePrefix(keyPrefix) + UUID.randomUUID() + extension(filename);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        return new StoredObject(key, publicUrl(key));
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .build());
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
