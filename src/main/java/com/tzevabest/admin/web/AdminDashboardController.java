package com.tzevabest.admin.web;

import com.tzevabest.admin.dto.ChartPoint;
import com.tzevabest.admin.dto.DashboardSummary;
import com.tzevabest.admin.dto.RecentRequestItem;
import com.tzevabest.admin.dto.TopItem;
import com.tzevabest.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Дашборд админки (T-13.1). Доступ — SUPPORT/MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPPORT','MANAGER','ADMIN')")
public class AdminDashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    public DashboardSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(from, to);
    }

    @GetMapping("/requests-chart")
    public List<ChartPoint> requestsChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        return service.requestsChart(from, to, granularity);
    }

    @GetMapping("/top-colors")
    public List<TopItem> topColors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return service.topColors(from, to, limit);
    }

    @GetMapping("/top-products")
    public List<TopItem> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return service.topProducts(from, to, limit);
    }

    @GetMapping("/top-categories")
    public List<TopItem> topCategories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return service.topCategories(from, to, limit);
    }

    @GetMapping("/recent-requests")
    public List<RecentRequestItem> recentRequests(@RequestParam(defaultValue = "10") int limit) {
        return service.recentRequests(limit);
    }
}
