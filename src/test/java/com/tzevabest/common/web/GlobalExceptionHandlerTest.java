package com.tzevabest.common.web;

import com.tzevabest.common.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет каждый обработчик {@link GlobalExceptionHandler}: HTTP-статус,
 * content-type {@code application/problem+json} и структуру тела по RFC 9457.
 */
@WebMvcTest(controllers = ErrorTestController.class,
        // Исключаем общий WebMvcConfig: его addPathPrefix("/api/v1") иначе префиксует
        // тестовый контроллер и ломает изоляцию среза обработчика ошибок.
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebMvcConfig.class))
@Import({GlobalExceptionHandler.class, WebErrorConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    private static String utf8(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ── BusinessException family ────────────────────────────────────────────────

    @Test
    void notFound_returns_404_problem_json() throws Exception {
        mvc.perform(get("/test-errors/not-found").header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.tzevabest.co.il/problems/catalog.product.not_found"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product with id 42 was not found."))
                .andExpect(jsonPath("$.instance").value("/test-errors/not-found"))
                .andExpect(jsonPath("$.code").value("catalog.product.not_found"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void conflict_returns_409() throws Exception {
        mvc.perform(get("/test-errors/conflict").header("Accept-Language", "en"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("catalog.sku.duplicate"))
                .andExpect(jsonPath("$.detail").value("A product with the same SKU already exists."));
    }

    @Test
    void businessValidation_returns_422() throws Exception {
        mvc.perform(get("/test-errors/validation").header("Accept-Language", "en"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("order.total.invalid"));
    }

    @Test
    void businessAccessDenied_returns_403() throws Exception {
        mvc.perform(get("/test-errors/access").header("Accept-Language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("order.access.denied"));
    }

    @Test
    void baseBusiness_returns_configured_status() throws Exception {
        mvc.perform(get("/test-errors/business").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.request.generic"))
                .andExpect(jsonPath("$.detail").value("The request could not be processed."));
    }

    @Test
    void externalService_returns_502_without_leaking_cause() throws Exception {
        MvcResult result = mvc.perform(get("/test-errors/external").header("Accept-Language", "en"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("payment.gateway.unavailable"))
                // 5xx: наружу общий текст, а не детали причины.
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred."))
                .andExpect(jsonPath("$.trace_id").isNotEmpty())
                .andReturn();
        assertThat(utf8(result)).doesNotContain("upstream 503");
    }

    // ── Валидация ───────────────────────────────────────────────────────────────

    @Test
    void methodArgumentNotValid_returns_400_with_field_errors() throws Exception {
        mvc.perform(post("/test-errors/body")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.validation.failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name must not be blank."));
    }

    @Test
    void constraintViolation_returns_400_with_field_errors() throws Exception {
        mvc.perform(get("/test-errors/constraint").param("value", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.validation.failed"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void malformedBody_returns_400() throws Exception {
        mvc.perform(post("/test-errors/body")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.request.malformed"));
    }

    @Test
    void typeMismatch_returns_400() throws Exception {
        mvc.perform(get("/test-errors/typed/abc").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.request.type_mismatch"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    // ── Прочие обработчики ──────────────────────────────────────────────────────

    @Test
    void dataIntegrity_returns_409_without_leaking_sql() throws Exception {
        MvcResult result = mvc.perform(get("/test-errors/data-integrity").header("Accept-Language", "en"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.data.conflict"))
                .andReturn();
        assertThat(utf8(result)).doesNotContain("uq_product_sku");
    }

    @Test
    void springAccessDenied_returns_403() throws Exception {
        mvc.perform(get("/test-errors/spring-access").header("Accept-Language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.access.denied"));
    }

    @Test
    void authentication_returns_401() throws Exception {
        mvc.perform(get("/test-errors/auth").header("Accept-Language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("common.auth.required"));
    }

    @Test
    void unexpected_returns_500_without_leaking_details() throws Exception {
        MvcResult result = mvc.perform(get("/test-errors/boom").header("Accept-Language", "en"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("common.internal.error"))
                .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred."))
                .andExpect(jsonPath("$.trace_id").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();
        assertThat(utf8(result)).doesNotContain("SECRET internal detail");
    }

    // ── Локализация по Accept-Language ──────────────────────────────────────────

    @Test
    void detail_localized_to_russian() throws Exception {
        MvcResult result = mvc.perform(get("/test-errors/not-found").header("Accept-Language", "ru"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(utf8(result)).contains("Товар с идентификатором 42 не найден");
    }

    @Test
    void detail_defaults_to_hebrew_without_accept_language() throws Exception {
        MvcResult result = mvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(utf8(result)).contains("מוצר עם המזהה 42 לא נמצא");
    }
}
