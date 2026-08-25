package com.tzevabest.common.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import com.tzevabest.common.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Единый обработчик ошибок REST API. Отдаёт {@code application/problem+json} по RFC 9457.
 * <p>
 * Тело ответа: {@code type, title, status, detail, instance} плюс расширения
 * {@code code} (машиночитаемый код), {@code trace_id} и, для ошибок валидации, {@code errors}.
 * Для 5xx детали внутренней ошибки наружу не отдаются — только общий текст и {@code trace_id};
 * полный стектрейс уходит в лог с тем же {@code trace_id}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://api.tzevabest.co.il/problems/";

    private static final String CODE_INTERNAL = "common.internal.error";
    private static final String CODE_VALIDATION = "common.validation.failed";
    private static final String CODE_MALFORMED = "common.request.malformed";
    private static final String CODE_TYPE_MISMATCH = "common.request.type_mismatch";
    private static final String CODE_CONFLICT = "common.data.conflict";
    private static final String CODE_ACCESS_DENIED = "common.access.denied";
    private static final String CODE_AUTH = "common.auth.required";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ── Бизнес-исключения (включая все подклассы BusinessException) ──────────────
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), resolve(ex.getCode(), ex.getArgs()), null, req, ex);
    }

    // ── Валидация тела запроса (@Valid @RequestBody) ────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                       HttpServletRequest req) {
        Locale locale = LocaleContextHolder.getLocale();
        List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), resolveFieldMessage(fe, locale)))
                .toList();
        return build(HttpStatus.BAD_REQUEST, CODE_VALIDATION, resolve(CODE_VALIDATION, null), errors, req, ex);
    }

    // ── Валидация параметров (@Validated + @Min/@NotNull на @RequestParam/@PathVariable) ─
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest req) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(lastNode(v.getPropertyPath()), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, CODE_VALIDATION, resolve(CODE_VALIDATION, null), errors, req, ex);
    }

    // ── Нечитаемое / повреждённое тело запроса ──────────────────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, CODE_MALFORMED, resolve(CODE_MALFORMED, null), null, req, ex);
    }

    // ── Несовпадение типа параметра ─────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        String detail = resolve(CODE_TYPE_MISMATCH, new Object[]{ex.getName(), ex.getValue()});
        List<ApiFieldError> errors = List.of(new ApiFieldError(ex.getName(), detail));
        return build(HttpStatus.BAD_REQUEST, CODE_TYPE_MISMATCH, detail, errors, req, ex);
    }

    // ── Нарушение целостности данных (в лог, без утечки SQL наружу) ──────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, CODE_CONFLICT, resolve(CODE_CONFLICT, null), null, req, ex);
    }

    // ── Отказ в доступе от Spring Security ──────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, CODE_ACCESS_DENIED, resolve(CODE_ACCESS_DENIED, null), null, req, ex);
    }

    // ── Ошибка аутентификации ───────────────────────────────────────────────────
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, CODE_AUTH, resolve(CODE_AUTH, null), null, req, ex);
    }

    // ── Последний рубеж: любое необработанное исключение ─────────────────────────
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Throwable ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, CODE_INTERNAL, resolve(CODE_INTERNAL, null), null, req, ex);
    }

    // ── Сборка ответа ───────────────────────────────────────────────────────────
    private ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String detail,
                                                List<ApiFieldError> errors, HttpServletRequest req, Throwable ex) {
        String traceId = currentTraceId();
        boolean serverError = status.is5xxServerError();

        if (serverError) {
            log.error("[trace_id={}] {} {} — code={}", traceId, status.value(), status.getReasonPhrase(), code, ex);
        } else {
            log.warn("[trace_id={}] {} {} — code={}: {}", traceId, status.value(), status.getReasonPhrase(),
                    code, ex.getMessage());
        }

        // Для 5xx детали внутренней ошибки наружу не отдаём — только общий текст.
        String safeDetail = serverError ? resolve(CODE_INTERNAL, null) : detail;
        List<ApiFieldError> safeErrors = serverError ? null : errors;

        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, safeDetail);
        body.setType(URI.create(PROBLEM_TYPE_BASE + code));
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(req.getRequestURI()));
        body.setProperty("code", code);
        body.setProperty("trace_id", traceId);
        if (safeErrors != null && !safeErrors.isEmpty()) {
            body.setProperty("errors", safeErrors);
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /** trace_id из MDC (проставлен RequestIdFilter из X-Request-Id) или новый, если фильтр не отработал. */
    private static String currentTraceId() {
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }

    private String resolve(String code, Object[] args) {
        try {
            return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return code;
        }
    }

    private String resolveFieldMessage(FieldError fe, Locale locale) {
        try {
            return messageSource.getMessage(fe, locale);
        } catch (NoSuchMessageException e) {
            return fe.getDefaultMessage() != null ? fe.getDefaultMessage() : fe.getCode();
        }
    }

    private static String lastNode(Path path) {
        String node = null;
        for (Path.Node n : path) {
            node = n.getName();
        }
        return node;
    }
}
