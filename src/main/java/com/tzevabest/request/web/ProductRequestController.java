package com.tzevabest.request.web;

import com.tzevabest.common.spam.FormTokenService;
import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.request.dto.ProductRequestCreate;
import com.tzevabest.request.dto.ProductRequestResponse;
import com.tzevabest.request.service.ProductRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичный приём заявок на товар (без аутентификации). Антиспам: honeypot (в сервисе) + rate limit
 * по IP и по телефону. Механизм лимитов — общий {@code @RateLimit} (тот же, что и на форме покраски
 * T-11.1, когда она появится).
 */
@RestController
@RequestMapping("/product-requests")
@RequiredArgsConstructor
public class ProductRequestController {

    private final ProductRequestService service;
    private final ClientIpResolver clientIpResolver;
    private final FormTokenService formTokens;

    /** Токен формы для проверки минимального времени заполнения (T-11.1.1). */
    @GetMapping("/form-token")
    public java.util.Map<String, String> formToken() {
        return java.util.Map.of("form_token", formTokens.issue());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(key = "ip", limit = 10, windowSeconds = 3600)
    @RateLimit(key = "phone:customerPhone", limit = 3, windowSeconds = 3600)
    public ProductRequestResponse create(@Valid @RequestBody ProductRequestCreate request,
                                         HttpServletRequest http) {
        // Язык витрины из Accept-Language (AcceptHeaderLocaleResolver) — на нём уйдёт подтверждение (R-53).
        String locale = http.getLocale() == null ? null : http.getLocale().getLanguage();
        return service.create(request, clientIpResolver.resolve(http), locale);
    }
}
