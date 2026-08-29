package com.tzevabest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.iam.service.JwtService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-11.2: API расчёта (диапазон, разбивка, оговорка, флаг оценки), влияние типа объекта и снимок
 * оценки в заявке, не меняющийся при изменении тарифа.
 */
class PricingApiTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtService jwt;

    /** resetState (TRUNCATE) стирает seed справочных таблиц — пере-сеиваем канонические тарифы. */
    @BeforeEach
    void reseedPricing() {
        jdbcTemplate.update("""
                insert into service_pricing_coefficients (code, multiplier) values
                    ('CEILING_HEIGHT_OVER_3M', 1.200), ('URGENCY', 1.300),
                    ('OPT_FURNITURE_REMOVAL', 1.050), ('OPT_SURFACE_PREP', 1.150),
                    ('OPT_PRIMING', 1.100), ('OPT_HEIGHT_WORK', 1.250)
                on conflict (code) do nothing""");
        jdbcTemplate.update("""
                insert into service_pricing_rules (property_type, work_type, price_per_sqm_min, price_per_sqm_max, min_order_price)
                select p.pt, w.wt, round((w.bmin * p.factor)::numeric, 2), round((w.bmax * p.factor)::numeric, 2), 500
                from (values ('APARTMENT', 1.0), ('HOUSE', 1.1), ('OFFICE', 1.2), ('COMMERCIAL', 1.3), ('OTHER', 1.0)) as p(pt, factor),
                     (values ('WALLS', 25, 40), ('CEILING', 30, 45), ('FACADE', 35, 55),
                             ('WOOD', 50, 80), ('METAL', 55, 85), ('DECORATIVE', 70, 120)) as w(wt, bmin, bmax)
                on conflict (property_type, work_type) do nothing""");
    }

    @Test
    void calculate_returns_estimate_with_breakdown_and_disclaimer() throws Exception {
        JsonNode r = json.readTree(calculate("APARTMENT", 100, "[\"WALLS\",\"CEILING\"]").getBody());
        assertThat(r.get("price_min").decimalValue()).isPositive();
        assertThat(r.get("price_max").decimalValue()).isGreaterThanOrEqualTo(r.get("price_min").decimalValue());
        assertThat(r.get("is_estimate").asBoolean()).isTrue();
        assertThat(r.get("disclaimer").asText()).isNotBlank();
        assertThat(r.get("breakdown")).hasSize(2);
    }

    @Test
    void property_type_affects_price() throws Exception {
        BigDecimal apartment = json.readTree(calculate("APARTMENT", 100, "[\"WALLS\"]").getBody())
                .get("price_min").decimalValue();
        BigDecimal office = json.readTree(calculate("OFFICE", 100, "[\"WALLS\"]").getBody())
                .get("price_min").decimalValue();
        assertThat(office).isGreaterThan(apartment); // офис дороже квартиры (наценка типа объекта)
    }

    @Test
    void intake_estimate_is_snapshotted_and_survives_tariff_change() throws Exception {
        // заявка на покраску → в неё пишется снимок оценки
        String body = "{\"customer_name\":\"Dana\",\"customer_phone\":\"0503330001\",\"property_type\":\"APARTMENT\","
                + "\"area_sqm\":100,\"ceiling_height_m\":2.7,\"work_types\":[\"WALLS\"],\"consent\":true}";
        ResponseEntity<String> created = rest.exchange("/api/v1/service-requests", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(null)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BigDecimal snapshot = jdbcTemplate.queryForObject(
                "select estimated_price_min from service_requests order by id desc limit 1", BigDecimal.class);
        assertThat(snapshot).isPositive();

        // поднимаем тариф APARTMENT/WALLS вдвое
        long ruleId = jdbcTemplate.queryForObject(
                "select id from service_pricing_rules where property_type='APARTMENT' and work_type='WALLS'", Long.class);
        ResponseEntity<String> upd = rest.exchange("/api/v1/admin/pricing-rules/" + ruleId, HttpMethod.PUT,
                new HttpEntity<>("{\"price_per_sqm_min\":50,\"price_per_sqm_max\":80,\"min_order_price\":500}",
                        jsonHeaders(managerToken())), String.class);
        assertThat(upd.getStatusCode()).isEqualTo(HttpStatus.OK);

        // новый расчёт дороже
        BigDecimal recalced = json.readTree(calculate("APARTMENT", 100, "[\"WALLS\"]").getBody())
                .get("price_min").decimalValue();
        assertThat(recalced).isGreaterThan(snapshot);

        // но снимок в уже созданной заявке не изменился
        BigDecimal stillSnapshot = jdbcTemplate.queryForObject(
                "select estimated_price_min from service_requests order by id desc limit 1", BigDecimal.class);
        assertThat(stillSnapshot).isEqualByComparingTo(snapshot);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<String> calculate(String propertyType, int area, String workTypesJson) {
        String body = "{\"property_type\":\"" + propertyType + "\",\"area_sqm\":" + area
                + ",\"ceiling_height_m\":2.7,\"work_types\":" + workTypesJson + "}";
        return rest.exchange("/api/v1/service-requests/calculate", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(null)), String.class);
    }

    private String managerToken() {
        return jwt.generateAccess(1L, "MANAGER", "m@x.test", "he");
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }
}
