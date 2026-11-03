package com.tzevabest.content.web;

import com.tzevabest.content.dto.SiteImageView;
import com.tzevabest.content.service.SiteImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Управление картинками сайта (только ADMIN): список слотов, загрузка, сброс к дефолту. */
@RestController
@RequestMapping("/admin/site-images")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSiteImageController {

    private final SiteImageService service;

    @GetMapping
    public List<SiteImageView> list() {
        return service.adminList();
    }

    @PostMapping(path = "/{slot}", consumes = "multipart/form-data")
    public SiteImageView upload(@PathVariable String slot, @RequestPart("file") MultipartFile file) {
        return service.upload(slot, file);
    }

    @DeleteMapping("/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable String slot) {
        service.reset(slot);
    }
}
