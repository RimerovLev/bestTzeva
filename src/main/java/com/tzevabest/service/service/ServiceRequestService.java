package com.tzevabest.service.service;

import com.tzevabest.common.spam.SpamGuard;
import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.service.ConsentService;
import com.tzevabest.media.service.MediaPresigner;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.media.service.StoredObjectInfo;
import com.tzevabest.service.domain.RequestSource;
import com.tzevabest.service.domain.ServiceMediaType;
import com.tzevabest.service.domain.ServiceRequest;
import com.tzevabest.service.domain.ServiceRequestMedia;
import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.dto.PresignItem;
import com.tzevabest.service.dto.PresignRequest;
import com.tzevabest.service.dto.PresignResponse;
import com.tzevabest.service.dto.PresignedUploadView;
import com.tzevabest.service.dto.ServiceRequestCreate;
import com.tzevabest.service.dto.ServiceRequestResponse;
import com.tzevabest.service.event.ServiceRequestCreatedEvent;
import com.tzevabest.service.repository.ServiceRequestMediaRepository;
import com.tzevabest.service.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Приём заявки на покраску от гостя (T-11.1). Согласие обязательно; номер — из последовательности БД
 * (как R-46); локаль клиента сохраняется (как R-53). Фото помещения — персональные данные: клиент
 * грузит их по presigned PUT в приватный бакет, в заявке приходят только ключи объектов. Антиспам
 * (honeypot, лимиты) усилит T-11.1.1. Ориентир — модуль request (заявки на товар).
 */
@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Set<String> LOCALES = Set.of("he", "ru", "en");
    private static final String DEFAULT_LOCALE = "he";
    private static final String PHOTO_PREFIX = "service-requests";
    /** Разрешённые типы фото помещения (аудит п.8): никаких SVG/произвольных типов. */
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic");
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024; // 10 МБ на фото

    private final ServiceRequestRepository requests;
    private final ServiceRequestMediaRepository media;
    private final ConsentService consents;
    private final MediaPresigner presigner;
    private final MediaStorage mediaStorage;
    private final SpamGuard spamGuard;
    private final PricingService pricing;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    /**
     * Presigned-ссылки на загрузку фото помещения в приватный бакет. Каждая выдача фиксируется в
     * media_upload_intents (ключ, ip): по ней осиротевшие загрузки убирает фоновая задача, а привязка
     * к заявке проверяется на подлинность ключа (R-67).
     */
    @Transactional
    public PresignResponse presignPhotos(PresignRequest req, String ip) {
        List<PresignedUploadView> uploads = new ArrayList<>();
        for (PresignItem file : req.files()) {
            // Whitelist конкретных типов (аудит п.8): image/svg+xml и прочее не выдаём presigned-ссылку.
            if (file.contentType() == null || !ALLOWED_PHOTO_TYPES.contains(file.contentType().toLowerCase(Locale.ROOT))) {
                throw new ValidationException("service_request.photo_type");
            }
            var presigned = presigner.presignUpload(PHOTO_PREFIX, file.filename(), file.contentType());
            jdbc.update("insert into media_upload_intents (object_key, ip) values (?, ?)",
                    presigned.objectKey(), ip);
            uploads.add(new PresignedUploadView(presigned.objectKey(), presigned.uploadUrl()));
        }
        return new PresignResponse(uploads);
    }

    @Transactional
    public ServiceRequestResponse create(ServiceRequestCreate req, Long userId, String ip, String localeParam) {
        // Honeypot / слишком быстрое заполнение по токену формы — тихо отбрасываем 201 (R-51),
        // общий механизм SpamGuard на обе формы (T-11.1.1). Блок-лист — после нормализации телефона.
        String honeypotReason = spamGuard.dropReason(req.website(), req.formToken(), ip, null);
        if (honeypotReason != null) {
            spamGuard.recordDrop("service_request", ip, null, honeypotReason);
            return droppedResponse();
        }
        if (!req.consent()) {
            throw new ValidationException("service_request.consent_required");
        }
        String locale = normalizeLocale(localeParam);
        String phone = PhoneNormalizer.normalize(req.customerPhone());
        if (spamGuard.isBlocked(ip, phone)) {
            spamGuard.recordDrop("service_request", ip, phone, SpamGuard.BLOCKED);
            return droppedResponse();
        }

        ServiceRequest request = new ServiceRequest();
        request.setRequestNumber(nextNumber());
        request.setUserId(userId);
        request.setCustomerName(req.customerName());
        request.setCustomerPhone(phone);
        request.setCustomerEmail(blankToNull(req.customerEmail()));
        request.setPropertyType(req.propertyType());
        request.setAreaSqm(req.areaSqm());
        request.setRoomsCount(req.roomsCount());
        request.setCeilingHeightM(req.ceilingHeightM());
        request.setWorkTypes(req.workTypes() == null ? EnumSet.noneOf(WorkType.class)
                : EnumSet.copyOf(req.workTypes()));
        request.setCity(blankToNull(req.city()));
        request.setAddress(blankToNull(req.address()));
        request.setPreferredDateFrom(req.preferredDateFrom());
        request.setPreferredDateTo(req.preferredDateTo());
        request.setComment(blankToNull(req.comment()));
        request.setStatus(ServiceRequestStatus.NEW);
        request.setSource(RequestSource.SITE_FORM);
        request.setConsentGivenAt(Instant.now());
        request.setLocale(locale);
        // Снимок предварительной оценки на момент создания (T-11.2): изменение тарифа позже её не меняет.
        var estimate = pricing.estimateAtIntake(req.propertyType(), req.areaSqm(), req.ceilingHeightM(),
                request.getWorkTypes());
        if (estimate != null) {
            request.setEstimatedPriceMin(estimate.priceMin());
            request.setEstimatedPriceMax(estimate.priceMax());
        }
        requests.save(request);

        int photoCount = attachPhotos(request, req.photoKeys());

        consents.recordDataProcessing(userId, phone, ip, "service_request");
        jdbc.update("insert into service_request_history (request_id, from_status, to_status) values (?, null, 'NEW')",
                request.getId());

        events.publishEvent(new ServiceRequestCreatedEvent(request.getId(), request.getRequestNumber(),
                phone, request.getCustomerEmail(), locale));

        return new ServiceRequestResponse(request.getId(), request.getRequestNumber(), request.getStatus(),
                request.getEstimatedPriceMin(), request.getEstimatedPriceMax(), request.getWorkTypes(), photoCount);
    }

    private int attachPhotos(ServiceRequest request, List<String> photoKeys) {
        if (photoKeys == null || photoKeys.isEmpty()) {
            return 0;
        }
        Set<String> keys = new LinkedHashSet<>(photoKeys); // дедуп
        if (keys.size() > 10) {
            throw new ValidationException("service_request.too_many_photos");
        }
        List<ServiceRequestMedia> rows = new ArrayList<>();
        for (String key : keys) {
            // Ключ должен быть реально выдан нами и ещё не использован (R-67): помечаем intent
            // использованным атомарно; 0 обновлённых строк — чужой/подобранный/повторный ключ.
            int claimed = jdbc.update(
                    "update media_upload_intents set used_at = now() where object_key = ? and used_at is null", key);
            if (claimed == 0 || !presigner.hasPrefix(key, PHOTO_PREFIX)) {
                throw new ValidationException("service_request.photo_key_invalid");
            }
            // Если объект уже загружен — проверяем фактический размер и тип (аудит п.8). presigned PUT не
            // ограничивает Content-Length, поэтому великана/не-картинку отбраковываем и удаляем из бакета.
            mediaStorage.headPrivate(key).ifPresent(info -> {
                if (info.contentLength() > MAX_PHOTO_BYTES || info.contentType() == null
                        || !ALLOWED_PHOTO_TYPES.contains(info.contentType().toLowerCase(Locale.ROOT))) {
                    mediaStorage.deletePrivate(key);
                    throw new ValidationException("service_request.photo_rejected");
                }
            });
            ServiceRequestMedia m = new ServiceRequestMedia();
            m.setRequest(request);
            m.setObjectKey(key);
            m.setType(ServiceMediaType.IMAGE);
            rows.add(m);
        }
        media.saveAll(rows);
        return rows.size();
    }

    /** Правдоподобный ответ на отсечённую (спам) заявку — неотличим от успешной, ничего не сохранено. */
    private static ServiceRequestResponse droppedResponse() {
        String fake = "SR-" + LocalDate.now(ZoneOffset.UTC).format(YYMMDD) + "-"
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));
        return new ServiceRequestResponse(0, fake, ServiceRequestStatus.NEW, null, null,
                EnumSet.noneOf(WorkType.class), 0);
    }

    /** Номер заявки из последовательности (как R-46). */
    String nextNumber() {
        Long seq = jdbc.queryForObject("select nextval('service_request_number_seq')", Long.class);
        return "SR-" + LocalDate.now(ZoneOffset.UTC).format(YYMMDD) + "-" + String.format("%04d", seq);
    }

    private static String normalizeLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return DEFAULT_LOCALE;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
