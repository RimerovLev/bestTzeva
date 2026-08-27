package com.tzevabest.catalog.web;

import com.tzevabest.catalog.dto.ColorSwatchResponse;
import com.tzevabest.catalog.dto.ColorSwatchSaveRequest;
import com.tzevabest.catalog.service.ColorPaletteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Админ-управление палитрой цветов. Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/colors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminColorController {

    private final ColorPaletteService palette;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColorSwatchResponse create(@Valid @RequestBody ColorSwatchSaveRequest request) {
        return palette.create(request);
    }

    @PutMapping("/{id}")
    public ColorSwatchResponse update(@PathVariable long id, @Valid @RequestBody ColorSwatchSaveRequest request) {
        return palette.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        palette.delete(id);
    }
}
