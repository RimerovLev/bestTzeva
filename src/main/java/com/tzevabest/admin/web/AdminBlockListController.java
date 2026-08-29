package com.tzevabest.admin.web;

import com.tzevabest.admin.dto.BlockListCreate;
import com.tzevabest.admin.dto.BlockListEntry;
import com.tzevabest.admin.service.BlockListAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Админ-управление блок-листом антиспама (IP/телефоны). Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/spam/block-list")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminBlockListController {

    private final BlockListAdminService service;

    @GetMapping
    public List<BlockListEntry> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockListEntry add(@Valid @RequestBody BlockListCreate request) {
        return service.add(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        service.remove(id);
    }
}
