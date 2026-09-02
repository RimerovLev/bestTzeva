package com.tzevabest.admin.web;

import com.tzevabest.admin.dto.SubjectRequest;
import com.tzevabest.admin.service.PrivacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Работа с ПДн (T-14.2). Доступ — только ADMIN. */
@RestController
@RequestMapping("/admin/privacy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrivacyController {

    private final PrivacyService privacy;

    @PostMapping("/export")
    public Map<String, Object> export(@Valid @RequestBody SubjectRequest request) {
        return privacy.exportSubject(request.contact());
    }

    @PostMapping("/anonymize")
    public Map<String, Object> anonymize(@Valid @RequestBody SubjectRequest request) {
        return Map.of("anonymized", privacy.anonymize(request.contact()));
    }
}
