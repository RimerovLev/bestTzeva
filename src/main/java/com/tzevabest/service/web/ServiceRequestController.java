package com.tzevabest.service.web;

import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.service.dto.PresignRequest;
import com.tzevabest.service.dto.PresignResponse;
import com.tzevabest.service.dto.ServiceRequestCreate;
import com.tzevabest.service.dto.ServiceRequestResponse;
import com.tzevabest.service.service.ServiceRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичный приём заявок на покраску (гостем или авторизованным). Антиспам: honeypot усилит T-11.1.1,
 * лимиты частоты по IP и нормализованному телефону — здесь (R-52).
 */
@RestController
@RequestMapping("/service-requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService service;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUser currentUser;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(key = "ip", limit = 5, windowSeconds = 3600)
    @RateLimit(key = "phone:customerPhone", limit = 3, windowSeconds = 3600)
    public ServiceRequestResponse create(@Valid @RequestBody ServiceRequestCreate request,
                                         HttpServletRequest http) {
        Long userId = currentUser.optional().map(p -> p.userId()).orElse(null);
        String locale = http.getLocale() == null ? null : http.getLocale().getLanguage();
        return service.create(request, userId, clientIpResolver.resolve(http), locale);
    }

    @PostMapping("/photos/presign")
    @RateLimit(key = "ip", limit = 20, windowSeconds = 3600)
    public PresignResponse presignPhotos(@Valid @RequestBody PresignRequest request) {
        return service.presignPhotos(request);
    }
}
