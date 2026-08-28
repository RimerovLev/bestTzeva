package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.MediaType;
import com.tzevabest.catalog.dto.ProductMediaView;
import com.tzevabest.catalog.service.ProductMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Админ-загрузка/удаление медиа товаров и вариантов (T-4.7). Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminProductMediaController {

    private final ProductMediaService media;

    @PostMapping(path = "/products/{productId}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductMediaView uploadProductMedia(
            @PathVariable long productId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "IMAGE") MediaType type,
            @RequestParam(name = "sort_order", defaultValue = "0") int sortOrder,
            @RequestParam(defaultValue = "false") boolean primary) {
        return media.uploadForProduct(productId, file, type, sortOrder, primary);
    }

    @PostMapping(path = "/variants/{variantId}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductMediaView uploadVariantMedia(
            @PathVariable long variantId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "IMAGE") MediaType type,
            @RequestParam(name = "sort_order", defaultValue = "0") int sortOrder,
            @RequestParam(defaultValue = "false") boolean primary) {
        return media.uploadForVariant(variantId, file, type, sortOrder, primary);
    }

    @DeleteMapping("/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable long id) {
        media.delete(id);
    }
}
