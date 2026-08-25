package com.tzevabest.common.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация безопасности web-слоя. Две взаимоисключающие цепочки по профилю, чтобы
 * «открытый baseline» физически не мог уехать в prod (fail-closed, а не TODO).
 * <ul>
 *   <li>{@code !prod} (local/test): документация и health открыты, остальное — permitAll
 *       до появления IAM;</li>
 *   <li>{@code prod}: swagger/api-docs закрыты (публичная схема — подарок для перебора
 *       эндпоинтов), health/info открыты для балансировщика, остальное — authenticated.
 *       Реальные ролевые правила и JWT-фильтр добавит IAM (T-3.2).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] DOC_PATHS = {
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final SecurityProblemSupport problemSupport;

    @Bean
    @Profile("!prod")
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DOC_PATHS).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // До появления IAM в dev/test API открыто.
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Profile("prod")
    SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .authorizeHttpRequests(auth -> auth
                        // Health/info нужны балансировщику и healthcheck.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Публичную схему API в prod не отдаём.
                        .requestMatchers(DOC_PATHS).denyAll()
                        // Всё остальное закрыто; ролевые правила добавит IAM (T-3.2).
                        .anyRequest().authenticated());
        return http.build();
    }
}
