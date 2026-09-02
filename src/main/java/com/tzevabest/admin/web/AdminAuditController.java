package com.tzevabest.admin.web;

import com.tzevabest.admin.dto.AuditEntryView;
import com.tzevabest.admin.service.AuditQueryService;
import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Журнал аудита. Доступ — только ADMIN. */
@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditQueryService service;

    @GetMapping
    public PageResponse<AuditEntryView> list(
            @RequestParam(name = "actor_id", required = false) Long actorUserId,
            @RequestParam(name = "entity_type", required = false) String entityType,
            @RequestParam(name = "entity_id", required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ApiPage PageQuery page) {
        return service.list(actorUserId, entityType, entityId, action, from, to, page);
    }

    @GetMapping("/entity/{type}/{id}")
    public List<AuditEntryView> entityHistory(@PathVariable String type, @PathVariable long id) {
        return service.entityHistory(type, id);
    }
}
