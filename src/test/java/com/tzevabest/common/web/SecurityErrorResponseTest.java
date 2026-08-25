package com.tzevabest.common.web;

import com.tzevabest.common.web.error.ApiErrorResponder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Неаутентифицированный запрос к защищённому пути должен получить problem+json (RFC 9457),
 * а не пустое тело: {@link SecurityProblemSupport} подключён как AuthenticationEntryPoint.
 * Это путь Spring Security (фильтры), куда @RestControllerAdvice не доходит.
 */
@WebMvcTest(controllers = SecurityErrorResponseTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebMvcConfig.class))
@Import({SecurityProblemSupport.class, ApiErrorResponder.class, WebErrorConfig.class,
        SecurityErrorResponseTest.SecuredChain.class})
@ActiveProfiles("test")
class SecurityErrorResponseTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unauthenticated_request_returns_401_problem_json() throws Exception {
        mvc.perform(get("/secured").header("Accept-Language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("common.auth.required"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecuredChain {
        @Bean
        SecurityFilterChain chain(HttpSecurity http, SecurityProblemSupport problemSupport) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(e -> e
                            .authenticationEntryPoint(problemSupport)
                            .accessDeniedHandler(problemSupport))
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated());
            return http.build();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/secured")
        String secured() {
            return "ok";
        }
    }
}
