package com.tzevabest.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/** R-69: превышение лимита загрузки отдаётся 413 problem+json, а не 500. Лимит занижен для теста. */
@TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=200B",
        "spring.servlet.multipart.max-request-size=300B"})
class MultipartLimitTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    @Test
    void oversized_upload_returns_413_problem_json() throws Exception {
        byte[] big = new byte[1024]; // > 200B
        java.util.Arrays.fill(big, (byte) 'a');

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.generateAccess(1L, "MANAGER", "m@x.test", "he"));
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(big) {
            @Override
            public String getFilename() {
                return "products.csv";
            }
        });
        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/products/import?dry_run=true",
                HttpMethod.POST, new HttpEntity<>(parts, h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(json.readTree(resp.getBody()).get("code").asText()).isEqualTo("common.upload.too_large");
    }
}
