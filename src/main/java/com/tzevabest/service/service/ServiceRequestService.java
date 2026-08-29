package com.tzevabest.service.service;

import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.service.ConsentService;
import com.tzevabest.media.service.MediaPresigner;
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

    private final ServiceRequestRepository requests;
    private final ServiceRequestMediaRepository media;
    private final ConsentService consents;
    private final MediaPresigner presigner;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    /** Presigned-ссылки на загрузку фото помещения в приватный бакет. */
    public PresignResponse presignPhotos(PresignRequest req) {
        List<PresignedUploadView> uploads = new ArrayList<>();
        for (PresignItem file : req.files()) {
            if (file.contentType() == null || !file.contentType().startsWith("image/")) {
                throw new ValidationException("service_request.photo_type");
            }
            var presigned = presigner.presignUpload(PHOTO_PREFIX, file.filename(), file.contentType());
            uploads.add(new PresignedUploadView(presigned.objectKey(), presigned.uploadUrl()));
        }
        return new PresignResponse(uploads);
    }

    @Transactional
    public ServiceRequestResponse create(ServiceRequestCreate req, Long userId, String ip, String localeParam) {
        if (!req.consent()) {
            throw new ValidationException("service_request.consent_required");
        }
        String locale = normalizeLocale(localeParam);
        String phone = PhoneNormalizer.normalize(req.customerPhone());

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
            if (!presigner.hasPrefix(key, PHOTO_PREFIX)) {
                throw new ValidationException("service_request.photo_key_invalid"); // чужой ключ
            }
            ServiceRequestMedia m = new ServiceRequestMedia();
            m.setRequest(request);
            m.setObjectKey(key);
            m.setType(ServiceMediaType.IMAGE);
            rows.add(m);
        }
        media.saveAll(rows);
        return rows.size();
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
