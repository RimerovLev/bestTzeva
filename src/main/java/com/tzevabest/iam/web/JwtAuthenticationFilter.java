package com.tzevabest.iam.web;

import com.tzevabest.common.web.security.AuthDetails;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.iam.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless-аутентификация по Bearer access-токену. Валидный токен наполняет SecurityContext
 * {@link AuthPrincipal} и ролью {@code ROLE_<role>}. Невалидный/просроченный — оставляет запрос
 * неаутентифицированным (дальше решает authorizeHttpRequests + AuthenticationEntryPoint).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            try {
                JwtService.AccessClaims claims = jwt.parseAccess(header.substring(BEARER.length()));
                AuthPrincipal principal = new AuthPrincipal(
                        claims.userId(), claims.role(), claims.email(), claims.locale());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
                // Реальный IP (X-Forwarded-For от доверенных прокси), а не адрес балансировщика.
                authentication.setDetails(new AuthDetails(clientIpResolver.resolve(request)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
