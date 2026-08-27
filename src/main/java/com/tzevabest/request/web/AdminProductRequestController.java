package com.tzevabest.request.web;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.request.domain.ProductRequestStatus;
import com.tzevabest.request.dto.ProductRequestAssign;
import com.tzevabest.request.dto.ProductRequestCommentInput;
import com.tzevabest.request.dto.ProductRequestDetail;
import com.tzevabest.request.dto.ProductRequestListItem;
import com.tzevabest.request.dto.ProductRequestMetrics;
import com.tzevabest.request.dto.ProductRequestTransition;
import com.tzevabest.request.service.ProductRequestAdminService;
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

import java.time.LocalDate;

/** Обработка заявок на товар менеджером. Доступ — SUPPORT/MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/product-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPPORT','MANAGER','ADMIN')")
public class AdminProductRequestController {

    private final ProductRequestAdminService service;

    @GetMapping
    public PageResponse<ProductRequestListItem> list(
            @RequestParam(required = false) ProductRequestStatus status,
            @RequestParam(name = "manager_id", required = false) Long managerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "in_stock", required = false) Boolean inStock,
            @RequestParam(name = "q", required = false) String query,
            @ApiPage(sort = {"created_at", "status", "request_number"}) PageQuery page) {
        return service.list(status, managerId, from, to, inStock, query, page);
    }

    @GetMapping("/metrics")
    public ProductRequestMetrics metrics() {
        return service.metrics();
    }

    @GetMapping("/{id}")
    public ProductRequestDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/transitions")
    public ProductRequestDetail transition(@PathVariable long id,
                                           @Valid @RequestBody ProductRequestTransition request) {
        return service.transition(id, request.to(), request.comment());
    }

    @PatchMapping("/{id}/assignee")
    public ProductRequestDetail assign(@PathVariable long id, @RequestBody ProductRequestAssign request) {
        return service.assign(id, request.managerId());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public void addComment(@PathVariable long id, @Valid @RequestBody ProductRequestCommentInput request) {
        service.addComment(id, request.text());
    }
}
