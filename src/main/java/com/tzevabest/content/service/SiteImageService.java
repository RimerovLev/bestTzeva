package com.tzevabest.content.service;

import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.content.domain.SiteImage;
import com.tzevabest.content.domain.SiteImageSlot;
import com.tzevabest.content.dto.SiteImageView;
import com.tzevabest.content.repository.SiteImageRepository;
import com.tzevabest.media.service.ContentTypeSniffer;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.media.service.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Картинки сайта по слотам (аудит-фича: овнер меняет их из админки). Файлы лежат в публичном бакете.
 * Тип проверяется по сигнатуре содержимого (не по словам клиента) — в бакет не должен попасть SVG/HTML
 * под видом картинки и раздаваться inline (stored XSS).
 */
@Service
@RequiredArgsConstructor
public class SiteImageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MiB
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final SiteImageRepository repo;
    private final MediaStorage storage;
    private final CurrentUser currentUser;

    /** Карта slot→публичный URL только для заданных слотов — витрина читает отсюда, иначе дефолт. */
    @Transactional(readOnly = true)
    public Map<String, String> publicMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (SiteImage img : repo.findAll()) {
            out.put(img.getSlot(), storage.publicUrl(img.getObjectKey()));
        }
        return out;
    }

    /** Все известные слоты с текущей картинкой (url=null, если не задана) — для админки. */
    @Transactional(readOnly = true)
    public List<SiteImageView> adminList() {
        Map<String, SiteImage> bySlot = new HashMap<>();
        for (SiteImage img : repo.findAll()) {
            bySlot.put(img.getSlot(), img);
        }
        List<SiteImageView> out = new ArrayList<>();
        for (SiteImageSlot slot : SiteImageSlot.values()) {
            SiteImage img = bySlot.get(slot.slug());
            out.add(new SiteImageView(
                    slot.slug(),
                    img != null ? storage.publicUrl(img.getObjectKey()) : null,
                    img != null ? img.getContentType() : null,
                    img != null ? img.getUpdatedAt().toString() : null));
        }
        return out;
    }

    @Transactional
    public SiteImageView upload(String slotSlug, MultipartFile file) {
        SiteImageSlot slot = SiteImageSlot.fromSlug(slotSlug)
                .orElseThrow(() -> new ValidationException("site_image.unknown_slot"));
        byte[] data = read(file);
        String safeType = ContentTypeSniffer.detect(data);
        if (safeType == null || !ALLOWED.contains(safeType)) {
            throw new ValidationException("site_image.invalid_type");
        }
        // Старый объект удаляем, чтобы не копить мусор в бакете.
        repo.findById(slot.slug()).ifPresent(prev -> safeDelete(prev.getObjectKey()));
        StoredObject obj = storage.put("site/" + slot.slug(), file.getOriginalFilename(), safeType, data);

        SiteImage entity = repo.findById(slot.slug()).orElseGet(SiteImage::new);
        entity.setSlot(slot.slug());
        entity.setObjectKey(obj.objectKey());
        entity.setContentType(safeType);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(currentUser.optional().map(p -> p.userId()).orElse(null));
        repo.save(entity);
        return new SiteImageView(slot.slug(), obj.url(), safeType, entity.getUpdatedAt().toString());
    }

    @Transactional
    public void reset(String slotSlug) {
        SiteImageSlot slot = SiteImageSlot.fromSlug(slotSlug)
                .orElseThrow(() -> new ValidationException("site_image.unknown_slot"));
        repo.findById(slot.slug()).ifPresent(img -> {
            safeDelete(img.getObjectKey());
            repo.delete(img);
        });
    }

    private void safeDelete(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // объект мог уже исчезнуть из бакета — не мешаем сбросу/замене
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("site_image.empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("site_image.too_large");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("site_image.unreadable");
        }
    }
}
