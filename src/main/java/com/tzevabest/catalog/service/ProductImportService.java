package com.tzevabest.catalog.service;

import com.tzevabest.catalog.dto.ImportError;
import com.tzevabest.catalog.dto.ImportResult;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Импорт каталога из CSV/XLSX (T-4.2.1). Режимы dry-run (только валидация и отчёт) и apply.
 * Существующие сопоставляются по SKU — обновление, а не дубль. Импорт построчно-транзакционный: одна
 * плохая строка не рушит весь импорт. Вставки/обновления вариантов идут обычными операциями, поэтому
 * денормализацию поддерживают statement-level триггеры (V33) — обхода нет (R-49).
 */
@Service
public class ProductImportService {

    private static final List<String> REQUIRED = List.of(
            "product_sku", "category_slug", "brand_slug", "paint_type", "name", "variant_sku",
            "color_code", "volume_liters", "price");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx; // построчная транзакция

    public ProductImportService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public ImportResult importFile(byte[] content, String filename, boolean dryRun) {
        List<Map<String, String>> rows = parse(content, filename);
        int[] counts = new int[3]; // created, updated, skipped
        List<ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 1;
            Map<String, String> row = rows.get(i);
            try {
                validate(row);
                if (dryRun) {
                    // только проверяем разрешимость ссылок; ничего не пишем
                    resolveCategory(row.get("category_slug"));
                    resolveBrand(row.get("brand_slug"));
                    counts[existsVariant(row.get("variant_sku")) ? 1 : 0]++;
                } else {
                    boolean updated = tx.execute(status -> applyRow(row));
                    counts[updated ? 1 : 0]++;
                }
            } catch (RowException e) {
                errors.add(new ImportError(rowNum, e.getMessage()));
                counts[2]++; // skipped
            } catch (RuntimeException e) {
                errors.add(new ImportError(rowNum, e.getMessage()));
                counts[2]++;
            }
        }
        return new ImportResult(dryRun, counts[0], counts[1], counts[2], errors);
    }

    /** Возвращает true, если вариант обновлён; false — если создан. Кидает {@link RowException} при ошибке. */
    private boolean applyRow(Map<String, String> row) {
        long categoryId = resolveCategory(row.get("category_slug"));
        long brandId = resolveBrand(row.get("brand_slug"));

        Long productId = jdbc.query("select id from products where sku = ?",
                rs -> rs.next() ? rs.getLong(1) : null, row.get("product_sku"));
        if (productId == null) {
            productId = jdbc.queryForObject("""
                    insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                    values (?, ?, ?, ?, ?, ?, ?, true) returning id""",
                    Long.class, categoryId, brandId, row.get("product_sku"),
                    upper(row.get("paint_type")), orDefault(row.get("usage_area"), "INDOOR"),
                    orDefault(row.get("finish"), "MATTE"), orDefault(row.get("base_type"), "WATER"));
            jdbc.update("insert into product_translations (product_id, locale, name) values (?, 'he', ?)",
                    productId, row.get("name"));
        }

        BigDecimal volume = new BigDecimal(row.get("volume_liters"));
        BigDecimal price = new BigDecimal(row.get("price"));
        Long variantId = jdbc.query("select id from product_variants where sku = ?",
                rs -> rs.next() ? rs.getLong(1) : null, row.get("variant_sku"));
        if (variantId == null) {
            jdbc.update("""
                    insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                    values (?, ?, ?, ?, ?, true)""",
                    productId, row.get("variant_sku"), row.get("color_code"), volume, price);
            return false; // created
        }
        jdbc.update("update product_variants set color_code = ?, volume_liters = ?, price = ?, updated_at = now() "
                + "where id = ?", row.get("color_code"), volume, price, variantId);
        return true; // updated
    }

    // ── валидация / резолв ────────────────────────────────────────────────────────

    private void validate(Map<String, String> row) {
        for (String key : REQUIRED) {
            String v = row.get(key);
            if (v == null || v.isBlank()) {
                throw new RowException("missing required column: " + key);
            }
        }
        try {
            new BigDecimal(row.get("volume_liters"));
            new BigDecimal(row.get("price"));
        } catch (NumberFormatException e) {
            throw new RowException("volume_liters/price must be numeric");
        }
    }

    private long resolveCategory(String slug) {
        Long id = jdbc.query("select id from categories where slug = ?", rs -> rs.next() ? rs.getLong(1) : null, slug);
        if (id == null) {
            throw new RowException("unknown category_slug: " + slug);
        }
        return id;
    }

    private long resolveBrand(String slug) {
        Long id = jdbc.query("select id from brands where slug = ?", rs -> rs.next() ? rs.getLong(1) : null, slug);
        if (id == null) {
            throw new RowException("unknown brand_slug: " + slug);
        }
        return id;
    }

    private boolean existsVariant(String sku) {
        Integer c = jdbc.queryForObject("select count(*) from product_variants where sku = ?", Integer.class, sku);
        return c != null && c > 0;
    }

    // ── парсинг ───────────────────────────────────────────────────────────────────

    private List<Map<String, String>> parse(byte[] content, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") ? parseXlsx(content) : parseCsv(content);
    }

    private List<Map<String, String>> parseCsv(byte[] content) {
        List<Map<String, String>> rows = new ArrayList<>();
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8).replace("﻿", "");
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            return rows;
        }
        String[] headers = split(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cells = split(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length; c++) {
                row.put(headers[c].trim(), c < cells.length ? cells[c].trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> parseXlsx(byte[] content) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : header) {
                headers.add(fmt.formatCellValue(cell).trim());
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row xr = sheet.getRow(r);
                if (xr == null) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    row.put(headers.get(c), fmt.formatCellValue(xr.getCell(c)).trim());
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new com.tzevabest.common.web.error.ValidationException("catalog.import.unreadable");
        }
        return rows;
    }

    private static String[] split(String line) {
        return line.split(",", -1);
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private static String orDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.toUpperCase(Locale.ROOT);
    }

    /** Ошибка одной строки — не рушит импорт. */
    private static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
