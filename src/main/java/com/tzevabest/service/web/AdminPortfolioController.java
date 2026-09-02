package com.tzevabest.service.web;

import com.tzevabest.service.dto.PortfolioCreate;
import com.tzevabest.service.dto.PortfolioMediaResponse;
import com.tzevabest.service.service.PortfolioAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Админ-управление портфолио. Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/portfolio")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminPortfolioController {

    private final PortfolioAdminService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody PortfolioCreate request) {
        return Map.of("id", service.create(request));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody PortfolioCreate request) {
        service.update(id, request);
    }

    @PatchMapping("/{id}/published")
    public void setPublished(@PathVariable long id, @RequestParam boolean value) {
        service.setPublished(id, value);
    }

    @PostMapping(path = "/{id}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioMediaResponse uploadMedia(
            @PathVariable long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam String kind,
            @RequestParam(name = "pair_group", required = false) String pairGroup,
            @RequestParam(name = "sort_order", required = false) Integer sortOrder) {
        return service.uploadMedia(id, file, kind, pairGroup, sortOrder);
    }

    @PostMapping("/from-request/{requestId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createFromRequest(@PathVariable long requestId) {
        return Map.of("id", service.createFromServiceRequest(requestId));
    }
}
