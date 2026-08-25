package com.tzevabest.iam.web;

import com.tzevabest.common.web.SecurityProblemSupport;
import com.tzevabest.iam.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] DOC_PATHS = {
            "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] AUTH_PUBLIC = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
    };
    private static final String[] AUTH_PROTECTED = {
            "/api/v1/auth/me", "/api/v1/auth/logout"
    };

    private final SecurityProblemSupport problemSupport;
    private final JwtService jwtService;

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
        cors.setAllowCredentials(true);
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
                        // До появления остальных модулей API открыто в dev/test.
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
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
    }
}
