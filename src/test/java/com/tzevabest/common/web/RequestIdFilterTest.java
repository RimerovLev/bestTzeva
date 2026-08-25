package com.tzevabest.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestIdFilter}: валидный X-Request-Id эхом возвращается и кладётся в MDC;
 * небезопасный (переводы строк, слишком длинный) заменяется сгенерированным.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String runWithHeader(String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(RequestIdFilter.HEADER, headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void valid_request_id_is_echoed() throws Exception {
        assertThat(runWithHeader("abc-123-DEF")).isEqualTo("abc-123-DEF");
    }

    @Test
    void missing_request_id_is_generated() throws Exception {
        String id = runWithHeader(null);
        assertThat(id).isNotBlank().hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void newline_in_request_id_is_rejected_and_generated() throws Exception {
        String id = runWithHeader("bad\nInjected: line");
        assertThat(id).doesNotContain("\n").doesNotContain("Injected");
        assertThat(id).matches("[A-Za-z0-9-]{1,64}");
    }

    @Test
    void overlong_request_id_is_rejected_and_generated() throws Exception {
        String overlong = "a".repeat(500);
        String id = runWithHeader(overlong);
        assertThat(id).isNotEqualTo(overlong).hasSizeLessThanOrEqualTo(64);
    }
}
