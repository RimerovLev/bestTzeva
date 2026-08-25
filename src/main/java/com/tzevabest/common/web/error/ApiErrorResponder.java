package com.tzevabest.common.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Единая сборка тела ошибки RFC 9457. Используется и {@link GlobalExceptionHandler}
 * (ошибки внутри DispatcherServlet), и security-обработчиками (401/403 из фильтров, куда
 * @RestControllerAdvice не доходит) — чтобы формат не разъехался между двумя путями.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorResponder {

    public static final String PROBLEM_TYPE_BASE = "https://api.tzevabest.co.il/problems/";

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    /** trace_id из MDC (проставлен RequestIdFilter из X-Request-Id) или новый. */
    public String currentTraceId() {
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }

    public String resolve(String code, Object[] args) {
        try {
            return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return code;
        }
    }

    public String resolveField(FieldError fieldError) {
        try {
            return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : fieldError.getCode();
        }
    }

    public ProblemDetail problem(HttpStatus status, String code, String detail, String instance,
                                 String traceId, List<ApiFieldError> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(PROBLEM_TYPE_BASE + code));
        body.setTitle(status.getReasonPhrase());
        if (instance != null) {
            body.setInstance(URI.create(instance));
        }
        body.setProperty("code", code);
        body.setProperty("trace_id", traceId);
        if (errors != null && !errors.isEmpty()) {
            body.setProperty("errors", errors);
        }
        return body;
    }

    /**
     * Запись problem+json прямо в ответ — для пути Spring Security (AuthenticationEntryPoint /
     * AccessDeniedHandler срабатывают в цепочке фильтров, до @RestControllerAdvice не доходят).
     */
    public void writeError(HttpServletRequest request, HttpServletResponse response,
                           HttpStatus status, String code) throws IOException {
        String traceId = currentTraceId();
        ProblemDetail body = problem(status, code, resolve(code, null), request.getRequestURI(), traceId, null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(RequestIdFilter.HEADER, traceId);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
