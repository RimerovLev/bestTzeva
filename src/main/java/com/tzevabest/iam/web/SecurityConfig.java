package com.tzevabest.iam.web;

import com.tzevabest.common.web.SecurityProblemSupport;
import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.iam.service.JwtService;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security web-слоя (владелец — iam). Stateless + JWT: {@link JwtAuthenticationFilter} перед
 * UsernamePasswordAuthenticationFilter; публичные пути через chain; 401/403 в problem+json через
 * общий {@link SecurityProblemSupport}. Две цепочки по профилю (открытый baseline не уедет в prod).
 * CORS-origin'ы — из переменной окружения. Пароли — BCrypt strength 12.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Служебные роли (роль «не ниже SUPPORT») — допуск в админ-зону. */
    private static final String[] STAFF_ROLES = {"SUPPORT", "WAREHOUSE", "MANAGER", "ADMIN"};

    private static final String[] DOC_PATHS = {
            "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] AUTH_PUBLIC = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
            "/api/v1/auth/email/verify", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset"
    };
    private static final String[] AUTH_PROTECTED = {
            "/api/v1/auth/me", "/api/v1/auth/logout",
            "/api/v1/auth/email/send-verification",
            "/api/v1/auth/phone/send-otp", "/api/v1/auth/phone/verify-otp",
            "/api/v1/auth/password/change"
    };

    private final SecurityProblemSupport problemSupport;
    private final JwtService jwtService;
    private final ClientIpResolver clientIpResolver;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        // Аутентификация — только через Authorization: Bearer, cookies не используются, поэтому
        // передавать учётные данные кросс-доменно не нужно. Если появится httpOnly-кука с refresh —
        // это отдельное архитектурное решение (тогда включить и записать в docs/). R-32.
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    @Profile("!prod")
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        common(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DOC_PATHS).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(AUTH_PUBLIC).permitAll()
                        .requestMatchers(AUTH_PROTECTED).authenticated()
                        // Публичный каталог категорий — только чтение, без аутентификации.
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // Публичная витрина товаров — только чтение.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/colors/**").permitAll()
                        // Публичный приём заявок на товар (гость).
                        .requestMatchers(HttpMethod.POST, "/api/v1/product-requests").permitAll()
                        // Админ-зона — только служебные роли; тонкие права — @PreAuthorize на сервисах.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(STAFF_ROLES)
                        // Клиентские ресурсы — только аутентифицированным (владение проверяет @ownership).
                        .requestMatchers("/api/v1/me/**").authenticated()
                        // До появления остальных модулей остальное открыто в dev/test.
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Profile("prod")
    SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        common(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(DOC_PATHS).denyAll()
                        .requestMatchers(AUTH_PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/colors/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/product-requests").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(STAFF_ROLES)
                        // Всё остальное закрыто; ролевые правила добавят следующие задачи.
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Общая часть обеих цепочек: stateless, CORS (бин corsConfigurationSource), JWT-фильтр, problem+json. */
    private HttpSecurity common(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, clientIpResolver),
                        UsernamePasswordAuthenticationFilter.class);
    }
}
