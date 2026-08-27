package com.tzevabest.catalog.web;

import com.tzevabest.catalog.dto.CategoryReorderRequest;
import com.tzevabest.catalog.dto.CategoryResponse;
import com.tzevabest.catalog.dto.CategorySaveRequest;
import com.tzevabest.catalog.service.CategoryService;
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

/** Админ-управление категориями. Доступ — MANAGER/ADMIN (зона /admin/** уже закрыта не-CUSTOMER). */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminCategoryController {

    private final CategoryService categories;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategorySaveRequest request) {
        return categories.create(request);
    }

    /** Массовое переупорядочивание. Объявлено до {@code /{id}}, чтобы не толковаться как id. */
    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody CategoryReorderRequest request) {
        categories.reorder(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable long id, @Valid @RequestBody CategorySaveRequest request) {
        return categories.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        categories.delete(id);
    }
}
