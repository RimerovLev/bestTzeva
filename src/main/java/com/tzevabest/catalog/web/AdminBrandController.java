package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.Brand;
import com.tzevabest.catalog.dto.BrandCreate;
import com.tzevabest.catalog.dto.BrandView;
import com.tzevabest.catalog.repository.BrandRepository;
import com.tzevabest.common.web.error.NotFoundException;
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
import java.util.Locale;

/** Справочник брендов для админки: список для выпадашки + создание нового. Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminBrandController {

    private final BrandRepository brands;

    @GetMapping
    public List<BrandView> list() {
        return brands.findByActiveTrueOrderByNameAsc().stream()
                .map(b -> new BrandView(b.getId(), b.getName(), b.getSlug()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BrandView create(@Valid @RequestBody BrandCreate request) {
        Brand brand = new Brand();
        brand.setName(request.name().trim());
        brand.setSlug(uniqueSlug(request.name()));
        brand.setActive(true);
        Brand saved = brands.save(brand);
        return new BrandView(saved.getId(), saved.getName(), saved.getSlug());
    }

    /** Мягкое удаление: бренд деактивируется (исчезает из выбора), товары с ним не трогаются. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        Brand brand = brands.findById(id).orElseThrow(() -> new NotFoundException("brand.not_found"));
        brand.setActive(false);
        brands.save(brand);
    }

    /** slug из названия (латиница/цифры → дефисы); для нелатинских имён — запасной «brand». Уникален. */
    private String uniqueSlug(String name) {
        String base = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "brand";
        }
        String slug = base;
        int n = 2;
        while (brands.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }
}
