package com.tzevabest.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T-13.2: цифры отчёта спроса сходятся с дашбордом; XLSX-экспорт с ивритскими заголовками. */
class ReportTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    @Test
    void demand_report_matches_dashboard_and_excludes_cancelled() throws Exception {
        long variant = seedVariant("RAL9010");
        seedRequest("COMPLETED", variant, "RAL9010", 5);
        seedRequest("COMPLETED", variant, "RAL9010", 3);
        seedRequest("CANCELLED", variant, "RAL9010", 100); // исключается из спроса

        // цифра дашборда
        JsonNode colors = json.readTree(get("/api/v1/admin/dashboard/top-colors", String.class).getBody());
        int dashQty = colors.get(0).get("quantity").asInt();
        assertThat(dashQty).isEqualTo(8);

        // та же цифра в отчёте (JSON)
        JsonNode report = json.readTree(get("/api/v1/admin/reports/demand", String.class).getBody());
        JsonNode row = report.get("rows").get(0);
        assertThat(row.get(0).asText()).isEqualTo("RAL9010");
        assertThat(row.get(1).asInt()).isEqualTo(dashQty);
    }

    @Test
    void xlsx_export_has_hebrew_headers() throws Exception {
        seedVariant("RAL1000");
        ResponseEntity<byte[]> resp = get("/api/v1/admin/reports/demand?format=xlsx&locale=he", byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).contains("spreadsheetml");

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(resp.getBody()))) {
            var header = wb.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("צבע");   // «цвет» на иврите
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("יחידות"); // «единицы»
            assertThat(wb.getSheetAt(0).isRightToLeft()).isTrue();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long seedVariant(String color) {
        String t = UUID.randomUUID().toString().substring(0, 8);
        long cat = jdbcTemplate.queryForObject("insert into categories (slug) values ('c-" + t + "') returning id", Long.class);
        long brand = jdbcTemplate.queryForObject("insert into brands (slug, name) values ('b-" + t + "','B') returning id", Long.class);
        long product = jdbcTemplate.queryForObject("""
                insert into products (category_id, brand_id, sku, paint_type, usage_area, finish, base_type, is_active)
                values (?, ?, ?, 'INTERIOR','INDOOR','MATTE','WATER', true) returning id""", Long.class, cat, brand, "P-" + t);
        return jdbcTemplate.queryForObject("""
                insert into product_variants (product_id, sku, color_code, volume_liters, price, is_active)
                values (?, ?, ?, 1.0, 100.00, true) returning id""", Long.class, product, "V-" + t, color);
    }

    private void seedRequest(String status, long variant, String color, int qty) {
        long id = jdbcTemplate.queryForObject("insert into product_requests "
                + "(request_number, customer_name, customer_phone, source, consent_given_at, policy_version, status) "
                + "values (?, 'C', '+972500000000', 'SITE_FORM', now(), '2026-08-01', ?) returning id",
                Long.class, "PR-" + UUID.randomUUID().toString().substring(0, 10), status);
        jdbcTemplate.update("""
                insert into product_request_items (request_id, variant_id, qty, product_name_snapshot,
                        sku_snapshot, price_snapshot, color_snapshot)
                values (?, ?, ?, 'P', 'SKU', 100.00, ?)""", id, variant, qty, color);
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@x.test", "he"));
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), type);
    }
}
