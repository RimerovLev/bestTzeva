package com.tzevabest.common.web.security;

import com.tzevabest.common.web.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
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

    /** IP из деталей аутентификации (проставлен JwtAuthenticationFilter), может быть null. */
    public String ip() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return null;
    }

    private AuthPrincipal require() {
        return optional().orElseThrow(() -> new BusinessException("common.auth.required", HttpStatus.UNAUTHORIZED));
    }
}
