package com.tzevabest.inventory.web;

import com.tzevabest.inventory.dto.WarehouseActiveRequest;
import com.tzevabest.inventory.dto.WarehouseCreateRequest;
import com.tzevabest.inventory.dto.WarehouseResponse;
import com.tzevabest.inventory.dto.WarehouseUpdateRequest;
import com.tzevabest.inventory.service.WarehouseAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD складов. Доступ — WAREHOUSE/MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAREHOUSE','MANAGER','ADMIN')")
public class AdminWarehouseController {

    private final WarehouseAdminService service;

    @GetMapping
    public List<WarehouseResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public WarehouseResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(@PathVariable long id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return service.update(id, request);
    }

    /** Активация/деактивация. Деактивация запрещена при остатках или активных резервах. */
    @PatchMapping("/{id}/active")
    public WarehouseResponse setActive(@PathVariable long id, @Valid @RequestBody WarehouseActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
