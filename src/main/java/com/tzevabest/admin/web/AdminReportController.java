package com.tzevabest.admin.web;

import com.tzevabest.admin.dto.Report;
import com.tzevabest.admin.service.ReportExporter;
import com.tzevabest.admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Отчёты админки (T-13.2). Доступ — MANAGER/ADMIN. format=xlsx|csv отдаёт файл, иначе JSON. */
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AdminReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;
    private final ReportExporter exporter;

    @GetMapping("/{type}")
    public ResponseEntity<?> report(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "he") String locale) {
        Report report = reports.build(type, from, to, locale);
        if ("xlsx".equalsIgnoreCase(format)) {
            byte[] body = exporter.toXlsx(report, "he".equalsIgnoreCase(locale));
            return file(body, XLSX, type + ".xlsx");
        }
        if ("csv".equalsIgnoreCase(format)) {
            return file(exporter.toCsv(report), MediaType.parseMediaType("text/csv"), type + ".csv");
        }
        return ResponseEntity.ok(report);
    }

    private ResponseEntity<byte[]> file(byte[] body, MediaType contentType, String filename) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
