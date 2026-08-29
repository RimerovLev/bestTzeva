package com.tzevabest.service.web;

import com.tzevabest.service.dto.PricingRuleUpdate;
import com.tzevabest.service.dto.PricingRuleView;
import com.tzevabest.service.service.PricingAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Админ-CRUD тарифов покраски. Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/pricing-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminPricingController {

    private final PricingAdminService service;

    @GetMapping
    public List<PricingRuleView> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public PricingRuleView update(@PathVariable long id, @Valid @RequestBody PricingRuleUpdate request) {
        return service.update(id, request);
    }
}
