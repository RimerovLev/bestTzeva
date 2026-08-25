package com.tzevabest.common.web;

import com.tzevabest.common.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Разбор и нормализация page/size/sort резолвером {@link ApiPageArgumentResolver}:
 * клэмп размера страницы (не ошибка, а обрезание) и allow-list полей сортировки (400).
 */
class ApiPageArgumentResolverTest {

    private final ApiPageArgumentResolver resolver = new ApiPageArgumentResolver();

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Probe())
            .setCustomArgumentResolvers(resolver)
            .build();

    // ── Клэмп размера/страницы ──────────────────────────────────────────────────

    @Test
    void size_over_max_is_clamped_to_100() throws Exception {
        mvc.perform(get("/probe").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void size_absent_defaults_to_20() throws Exception {
        mvc.perform(get("/probe"))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void size_below_one_falls_back_to_default() throws Exception {
        mvc.perform(get("/probe").param("size", "0"))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void negative_page_is_normalized_to_zero() throws Exception {
        mvc.perform(get("/probe").param("page", "-3"))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void size_at_max_is_kept() throws Exception {
        mvc.perform(get("/probe").param("size", "100"))
                .andExpect(jsonPath("$.size").value(100));
    }

    // ── Сортировка ──────────────────────────────────────────────────────────────

    @Test
    void allowed_sort_fields_are_parsed_with_direction() throws Exception {
        mvc.perform(get("/probe").param("sort", "name,desc").param("sort", "price"))
                .andExpect(jsonPath("$.sort[0]").value("name:DESC"))
                .andExpect(jsonPath("$.sort[1]").value("price:ASC"));
    }

    @Test
    void unknown_sort_field_is_rejected() throws Exception {
        MethodParameter parameter = restrictedParameter();
        ServletWebRequest request = requestWithSort("secret_column");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, request, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sort_is_rejected_when_no_fields_allowed() throws Exception {
        MethodParameter parameter = unrestrictedParameter();
        ServletWebRequest request = requestWithSort("name");

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, request, null))
                .isInstanceOf(BadRequestException.class);
    }

    private static ServletWebRequest requestWithSort(String sort) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("sort", sort);
        return new ServletWebRequest(req);
    }

    private static MethodParameter restrictedParameter() throws NoSuchMethodException {
        Method m = Probe.class.getDeclaredMethod("echo", PageQuery.class);
        return new MethodParameter(m, 0);
    }

    private static MethodParameter unrestrictedParameter() throws NoSuchMethodException {
        Method m = Probe.class.getDeclaredMethod("echoNoSort", PageQuery.class);
        return new MethodParameter(m, 0);
    }

    @RestController
    static class Probe {
        @GetMapping("/probe")
        Map<String, Object> echo(@ApiPage(sort = {"name", "price"}) PageQuery query) {
            List<String> sort = query.sort().orders().stream()
                    .map(o -> o.property() + ":" + o.direction())
                    .toList();
            return Map.of("page", query.page(), "size", query.size(), "sort", sort);
        }

        @GetMapping("/probe-no-sort")
        Map<String, Object> echoNoSort(@ApiPage PageQuery query) {
            return Map.of("page", query.page(), "size", query.size());
        }
    }
}
