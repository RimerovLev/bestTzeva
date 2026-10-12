package com.tzevabest.service.web;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.media.service.MediaPresigner;
import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.service.dto.AssignRequest;
import com.tzevabest.service.dto.CommentInput;
import com.tzevabest.service.dto.MeasurementRequest;
import com.tzevabest.service.dto.QuoteRequest;
import com.tzevabest.service.dto.ServiceRequestDetail;
import com.tzevabest.service.dto.ServiceRequestListItem;
import com.tzevabest.service.dto.ServiceRequestMediaView;
import com.tzevabest.service.dto.ServiceRequestMetrics;
import com.tzevabest.service.dto.TransitionRequest;
import com.tzevabest.service.service.ServiceRequestAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Воронка заявок на покраску в админке. Доступ — SUPPORT/MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/service-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPPORT','MANAGER','ADMIN')")
public class AdminServiceRequestController {

    private final ServiceRequestAdminService service;
    private final MediaPresigner presigner;

    @GetMapping
    public PageResponse<ServiceRequestListItem> list(
            @RequestParam(required = false) ServiceRequestStatus status,
            @RequestParam(name = "property_type", required = false) PropertyType propertyType,
            @RequestParam(required = false) String city,
            @RequestParam(name = "manager_id", required = false) Long managerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "area_min", required = false) BigDecimal areaMin,
            @RequestParam(name = "area_max", required = false) BigDecimal areaMax,
            @RequestParam(required = false) String source,
            @RequestParam(name = "q", required = false) String query,
            @ApiPage(sort = {"created_at", "status", "request_number", "area_sqm"}) PageQuery page) {
        return service.list(status, propertyType, city, managerId, from, to, areaMin, areaMax, source, query, page);
    }

    @GetMapping("/metrics")
    public ServiceRequestMetrics metrics() {
        return service.metrics();
    }

    @GetMapping("/{id}")
    public ServiceRequestDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/transitions")
    public ServiceRequestDetail transition(@PathVariable long id, @Valid @RequestBody TransitionRequest request) {
        return service.transition(id, request.to(), request.comment());
    }

    @PatchMapping("/{id}/assignee")
    public ServiceRequestDetail assign(@PathVariable long id, @RequestBody AssignRequest request) {
        return service.assign(id, request.managerId());
    }

    @PostMapping("/{id}/auto-assign")
    public ServiceRequestDetail autoAssign(@PathVariable long id) {
        return service.autoAssign(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public void addComment(@PathVariable long id, @Valid @RequestBody CommentInput request) {
        service.addComment(id, request.text());
    }

    @PostMapping("/{id}/measurement")
    public ServiceRequestDetail scheduleMeasurement(@PathVariable long id,
                                                    @Valid @RequestBody MeasurementRequest request) {
        return service.scheduleMeasurement(id, request.at());
    }

    @PostMapping("/{id}/quote")
    public ServiceRequestDetail quote(@PathVariable long id, @Valid @RequestBody QuoteRequest request) {
        return service.quote(id, request);
    }

    /** Фото клиента: presigned GET на каждое (живут минуты — фронт запрашивает при открытии карточки). */
    @GetMapping("/{id}/media")
    public List<ServiceRequestMediaView> media(@PathVariable long id) {
        return service.mediaKeys(id).stream()
                .map(m -> new ServiceRequestMediaView(m.id(), m.type(), m.uploadedAt(),
                        presigner.presignDownload(m.objectKey())))
                .toList();
    }

    @GetMapping("/{id}/quote-pdf")
    public Map<String, String> quotePdf(@PathVariable long id) {
        return Map.of("url", presigner.presignDownload(service.quotePdfKey(id)));
    }
}
