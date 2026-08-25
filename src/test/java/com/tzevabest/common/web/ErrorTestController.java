package com.tzevabest.common.web;

import com.tzevabest.common.web.error.AccessDeniedBusinessException;
import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.ExternalServiceException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Тестовый контроллер: каждый эндпоинт провоцирует ровно одно исключение,
 * которое должен перехватить {@code GlobalExceptionHandler}.
 */
@RestController
@Validated
@RequestMapping("/test-errors")
class ErrorTestController {

    @GetMapping("/not-found")
    String notFound() {
        throw new NotFoundException("catalog.product.not_found", 42);
    }

    @GetMapping("/conflict")
    String conflict() {
        throw new ConflictException("catalog.sku.duplicate");
    }

    @GetMapping("/validation")
    String validation() {
        throw new ValidationException("order.total.invalid");
    }

    @GetMapping("/access")
    String access() {
        throw new AccessDeniedBusinessException("order.access.denied");
    }

    @GetMapping("/external")
    String external() {
        throw new ExternalServiceException("payment.gateway.unavailable",
                new RuntimeException("upstream 503: internal gateway body"));
    }

    @GetMapping("/business")
    String business() {
        throw new BusinessException("common.request.generic", HttpStatus.BAD_REQUEST);
    }

    @PostMapping("/body")
    String body(@Valid @RequestBody SampleDto dto) {
        return dto.name();
    }

    @GetMapping("/typed/{id}")
    String typed(@PathVariable long id) {
        return String.valueOf(id);
    }

    @GetMapping("/constraint")
    String constraint(@RequestParam @Min(1) long value) {
        return String.valueOf(value);
    }

    @GetMapping("/data-integrity")
    String dataIntegrity() {
        throw new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_product_sku\"");
    }

    @GetMapping("/spring-access")
    String springAccess() {
        throw new org.springframework.security.access.AccessDeniedException("denied by security");
    }

    @GetMapping("/auth")
    String auth() {
        throw new BadCredentialsException("bad credentials internal");
    }

    @GetMapping("/boom")
    String boom() {
        throw new IllegalStateException("SECRET internal detail: db password leaked");
    }

    record SampleDto(@NotBlank(message = "{sample.name.required}") String name) {
    }
}
