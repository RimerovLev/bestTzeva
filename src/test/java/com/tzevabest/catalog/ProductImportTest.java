package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/** T-4.2.1: импорт каталога — dry-run, обновление по SKU, построчные ошибки, 5000 строк. */
class ProductImportTest extends IntegrationTest {

    private static final String HEADER =
            "product_sku,category_slug,brand_slug,paint_type,name,variant_sku,color_code,volume_liters,price";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into categories (slug) values ('cat-x')");
        jdbcTemplate.update("insert into brands (slug, name) values ('brand-x','B')");
    }

    @Test
    void dry_run_validates_and_reports_without_writing() throws Exception {
        String csv = HEADER + "\n"
                + "P1,cat-x,brand-x,INTERIOR,Blue,V1,RAL1,1.0,100\n"
                + "P2,nope,brand-x,INTERIOR,Red,V2,RAL2,1.0,100\n";      // неизвестная категория
        JsonNode res = json.readTree(upload(csv, true).getBody());
        assertThat(res.get("dry_run").asBoolean()).isTrue();
        assertThat(res.get("created").asInt()).isEqualTo(1);
        assertThat(res.get("skipped").asInt()).isEqualTo(1);
        assertThat(res.get("errors").get(0).get("row").asInt()).isEqualTo(2);
        assertThat(res.get("errors").get(0).get("reason").asText()).contains("category_slug");

        // ничего не записано
        Integer products = jdbcTemplate.queryForObject("select count(*) from products", Integer.class);
        assertThat(products).isZero();
    }

    @Test
    void apply_creates_then_updates_by_sku() throws Exception {
        String create = HEADER + "\nP1,cat-x,brand-x,INTERIOR,Blue,V1,RAL1,1.0,100\n";
        JsonNode c = json.readTree(upload(create, false).getBody());
        assertThat(c.get("created").asInt()).isEqualTo(1);

        // повторный импорт того же variant_sku с новой ценой — обновление, не дубль
        String update = HEADER + "\nP1,cat-x,brand-x,INTERIOR,Blue,V1,RAL1,1.0,150\n";
        JsonNode u = json.readTree(upload(update, false).getBody());
        assertThat(u.get("updated").asInt()).isEqualTo(1);
        assertThat(u.get("created").asInt()).isZero();

        Integer variants = jdbcTemplate.queryForObject("select count(*) from product_variants", Integer.class);
        assertThat(variants).isEqualTo(1); // один вариант, не дубль
        java.math.BigDecimal price = jdbcTemplate.queryForObject(
                "select price from product_variants where sku = 'V1'", java.math.BigDecimal.class);
        assertThat(price).isEqualByComparingTo("150");
    }

    @Test
    void imports_five_thousand_rows() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < 5000; i++) {
            csv.append("P").append(i).append(",cat-x,brand-x,INTERIOR,Name,V").append(i)
                    .append(",RAL1,1.0,100\n");
        }
        long start = System.currentTimeMillis();
        JsonNode res = json.readTree(upload(csv.toString(), false).getBody());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(res.get("created").asInt()).isEqualTo(5000);
        assertThat(res.get("skipped").asInt()).isZero();
        Integer variants = jdbcTemplate.queryForObject("select count(*) from product_variants", Integer.class);
        assertThat(variants).isEqualTo(5000);
        System.out.println("IMPORT_5000_MS=" + elapsed);
    }

    private ResponseEntity<String> upload(String csv, boolean dryRun) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@x.test", "he"));
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(csv.getBytes()) {
            @Override
            public String getFilename() {
                return "products.csv";
            }
        });
        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/products/import?dry_run=" + dryRun,
                HttpMethod.POST, new HttpEntity<>(parts, h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp;
    }
}
