package com.tzevabest.catalog.web;

import com.tzevabest.catalog.dto.ImportResult;
import com.tzevabest.catalog.service.ProductImportService;
import com.tzevabest.common.web.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Импорт каталога из CSV/XLSX (T-4.2.1). Доступ — MANAGER/ADMIN. */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminProductImportController {

    private final ProductImportService importer;

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    public ImportResult importProducts(@RequestPart("file") MultipartFile file,
                                       @RequestParam(name = "dry_run", defaultValue = "true") boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("catalog.import.empty");
        }
        try {
            return importer.importFile(file.getBytes(), file.getOriginalFilename(), dryRun);
        } catch (IOException e) {
            throw new ValidationException("catalog.import.unreadable");
        }
    }
}
