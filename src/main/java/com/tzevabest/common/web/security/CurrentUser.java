package com.tzevabest.common.web.security;

import com.tzevabest.common.web.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Доступ к текущему пользователю из SecurityContext — через инъекцию бина (не статикой).
 * id/роль/локаль берутся из access-токена (принципал {@link AuthPrincipal}).
 */
@Component
public class CurrentUser {

    public Optional<AuthPrincipal> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public boolean isAuthenticated() {
        return optional().isPresent();
    }

    public long id() {
        return require().userId();
    }

    public String role() {
        return require().role();
    }

    public String locale() {
        return require().locale();
    }

    public String email() {
        return require().email();
    }

    /** Реальный IP из деталей аутентификации (проставлен JwtAuthenticationFilter через ClientIpResolver). */
    public String ip() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof AuthDetails details) {
            return details.ip();
        }
        return null;
    }

    private AuthPrincipal require() {
        return optional().orElseThrow(() -> new BusinessException("common.auth.required", HttpStatus.UNAUTHORIZED));
    }
}
