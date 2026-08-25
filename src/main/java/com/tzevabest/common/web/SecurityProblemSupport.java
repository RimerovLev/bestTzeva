package com.tzevabest.common.web;

import com.tzevabest.common.web.error.ApiErrorResponder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Обработчики Spring Security для 401/403, отдающие то же тело problem+json, что и
 * {@link com.tzevabest.common.web.error.GlobalExceptionHandler} — через общий
 * {@link ApiErrorResponder}. Подключаются в обеих цепочках {@code SecurityConfig}.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemSupport implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrorResponder responder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        responder.writeError(request, response, HttpStatus.UNAUTHORIZED, "common.auth.required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        responder.writeError(request, response, HttpStatus.FORBIDDEN, "common.access.denied");
    }
}
