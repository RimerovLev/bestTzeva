package com.tzevabest.catalog.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.catalog.domain.MediaType;
import com.tzevabest.catalog.dto.ProductMediaView;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.media.service.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Админ-управление медиа товаров/вариантов (T-4.7): загрузка файла в S3-хранилище и вставка строки
 * product_media, удаление (объект + строка). Единственность главного изображения обеспечивается
 * снятием старого primary в той же транзакции (плюс частичный уникальный индекс как страховка).
 */
@Service
@RequiredArgsConstructor
public class ProductMediaService {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MiB

    private final JdbcTemplate jdbc;
    private final MediaStorage storage;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @Transactional
    public ProductMediaView uploadForProduct(long productId, MultipartFile file, MediaType type,
                                             int sortOrder, boolean primary) {
        if (count("select count(*) from products where id = ?", productId) == 0) {
            throw new NotFoundException("catalog.product.not_found");
        }
        return store("products/" + productId, "product_id", productId, file, type, sortOrder, primary);
    }

    @Transactional
    public ProductMediaView uploadForVariant(long variantId, MultipartFile file, MediaType type,
                                             int sortOrder, boolean primary) {
        if (count("select count(*) from product_variants where id = ?", variantId) == 0) {
            throw new NotFoundException("catalog.variant.not_found");
        }
        return store("variants/" + variantId, "variant_id", variantId, file, type, sortOrder, primary);
    }

    @Transactional
    public void delete(long mediaId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("select object_key from product_media where id = ?", mediaId);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("catalog.media.not_found");
        }
        String objectKey = (String) row.get("object_key");
        if (objectKey != null) {
            storage.delete(objectKey);
        }
        jdbc.update("delete from product_media where id = ?", mediaId);
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                "product_media.delete", "product_media", mediaId, Map.of());
    }

    // ownerColumn — внутренний литерал ("product_id"/"variant_id"), не пользовательский ввод.
    private ProductMediaView store(String prefix, String ownerColumn, long ownerId, MultipartFile file,
                                   MediaType type, int sortOrder, boolean primary) {
        byte[] data = validate(file, type);
        StoredObject obj = storage.put(prefix, file.getOriginalFilename(), file.getContentType(), data);

        if (primary) {
            // Снимаем прежнее главное у этого владельца — иначе уникальный индекс даст конфликт.
            jdbc.update("update product_media set is_primary = false where " + ownerColumn + " = ? and is_primary",
                    ownerId);
        }
        Long id = jdbc.queryForObject("insert into product_media (" + ownerColumn
                        + ", url, object_key, type, sort_order, is_primary) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, ownerId, obj.url(), obj.objectKey(), type.name(), sortOrder, primary);

        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                "product_media.upload", "product_media", id, Map.of(ownerColumn, ownerId, "url", obj.url()));
        return new ProductMediaView(id, obj.url(), type, sortOrder, primary, null);
    }

    /** Проверяет размер и соответствие MIME-типа заявленному {@link MediaType}, возвращает содержимое. */
    private byte[] validate(MultipartFile file, MediaType type) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("catalog.media.empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("catalog.media.too_large");
        }
        String contentType = file.getContentType();
        if (contentType == null || !matchesType(type, contentType)) {
            throw new ValidationException("catalog.media.invalid_type");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("catalog.media.unreadable");
        }
    }

    private static boolean matchesType(MediaType type, String contentType) {
        return switch (type) {
            case IMAGE -> contentType.startsWith("image/");
            case VIDEO -> contentType.startsWith("video/");
            case DOC -> contentType.startsWith("application/") || contentType.equals("text/plain");
        };
    }

    private long count(String sql, long id) {
        Long c = jdbc.queryForObject(sql, Long.class, id);
        return c == null ? 0 : c;
    }
}
