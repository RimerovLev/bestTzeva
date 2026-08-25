package com.tzevabest.common.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Единый обработчик ошибок REST API. Отдаёт {@code application/problem+json} по RFC 9457.
 * Сборка тела делегируется {@link ApiErrorResponder} — тот же код формирует ответ и на пути
 * Spring Security (401/403 из фильтров).
 * <p>
 * Для 5xx детали внутренней ошибки наружу не отдаются — только общий текст и {@code trace_id};
 * полный стектрейс уходит в лог с тем же {@code trace_id}. Последний рубеж — {@link Exception}
 * (а не {@link Throwable}): {@code Error} вроде OOM в HTTP-ответ не превращаем.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String CODE_INTERNAL = "common.internal.error";
    private static final String CODE_VALIDATION = "common.validation.failed";
    private static final String CODE_MALFORMED = "common.request.malformed";
    private static final String CODE_TYPE_MISMATCH = "common.request.type_mismatch";
    private static final String CODE_CONFLICT = "common.data.conflict";
    private static final String CODE_ACCESS_DENIED = "common.access.denied";
    private static final String CODE_AUTH = "common.auth.required";
    private static final String CODE_NOT_FOUND = "common.resource.not_found";
    private static final String CODE_METHOD_NOT_ALLOWED = "common.method.not_allowed";
    private static final String CODE_MEDIA_TYPE = "common.media_type.not_supported";

    private final ApiErrorResponder responder;

    // ── Бизнес-исключения (включая все подклассы BusinessException) ──────────────
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), responder.resolve(ex.getCode(), ex.getArgs()), null, req, ex);
    }

    // ── Валидация тела запроса (@Valid @RequestBody) ────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                       HttpServletRequest req) {
        List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiFieldError(fe.getField(), responder.resolveField(fe)))
                .toList();
        return build(HttpStatus.BAD_REQUEST, CODE_VALIDATION, responder.resolve(CODE_VALIDATION, null), errors, req, ex);
    }

    // ── Валидация параметров (@Validated + @Min/@NotNull на @RequestParam/@PathVariable) ─
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest req) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(lastNode(v.getPropertyPath()), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, CODE_VALIDATION, responder.resolve(CODE_VALIDATION, null), errors, req, ex);
    }

    // ── Нечитаемое / повреждённое тело запроса ──────────────────────────────────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, CODE_MALFORMED, responder.resolve(CODE_MALFORMED, null), null, req, ex);
    }

    // ── Несовпадение типа параметра ─────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        String detail = responder.resolve(CODE_TYPE_MISMATCH, new Object[]{ex.getName(), ex.getValue()});
        List<ApiFieldError> errors = List.of(new ApiFieldError(ex.getName(), detail));
        return build(HttpStatus.BAD_REQUEST, CODE_TYPE_MISMATCH, detail, errors, req, ex);
    }

    // ── Несуществующий путь (404) ───────────────────────────────────────────────
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, responder.resolve(CODE_NOT_FOUND, null), null, req, ex);
    }

    // ── Метод не поддерживается (405) ───────────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, CODE_METHOD_NOT_ALLOWED,
                responder.resolve(CODE_METHOD_NOT_ALLOWED, null), null, req, ex);
    }

    // ── Неподдерживаемый Content-Type (415) ─────────────────────────────────────
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CODE_MEDIA_TYPE,
                responder.resolve(CODE_MEDIA_TYPE, null), null, req, ex);
    }

    // ── Нарушение целостности данных (в лог, без утечки SQL наружу) ──────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, CODE_CONFLICT, responder.resolve(CODE_CONFLICT, null), null, req, ex);
    }

    // ── Отказ в доступе / ошибка аутентификации, выброшенные внутри DispatcherServlet ──
    // (напр. method-security @PreAuthorize). Фильтровый путь 401/403 обслуживает SecurityProblemSupport.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, CODE_ACCESS_DENIED, responder.resolve(CODE_ACCESS_DENIED, null), null, req, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, CODE_AUTH, responder.resolve(CODE_AUTH, null), null, req, ex);
    }

    // ── Последний рубеж: любое необработанное исключение (не Error) ──────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, CODE_INTERNAL, responder.resolve(CODE_INTERNAL, null), null, req, ex);
    }

    // ── Сборка ответа ───────────────────────────────────────────────────────────
    private ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String detail,
                                                List<ApiFieldError> errors, HttpServletRequest req, Throwable ex) {
        String traceId = responder.currentTraceId();
        boolean serverError = status.is5xxServerError();

        if (serverError) {
            log.error("[trace_id={}] {} {} — code={}", traceId, status.value(), status.getReasonPhrase(), code, ex);
        } else {
            log.warn("[trace_id={}] {} {} — code={}: {}", traceId, status.value(), status.getReasonPhrase(),
                    code, ex.getMessage());
        }

        // Для 5xx детали внутренней ошибки наружу не отдаём — только общий текст.
        String safeDetail = serverError ? responder.resolve(CODE_INTERNAL, null) : detail;
        List<ApiFieldError> safeErrors = serverError ? null : errors;

        ProblemDetail body = responder.problem(status, code, safeDetail, req.getRequestURI(), traceId, safeErrors);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String lastNode(Path path) {
        String node = null;
        for (Path.Node n : path) {
            node = n.getName();
        }
        return node;
    }
}
