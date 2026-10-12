package com.tzevabest.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Документация OpenAPI/Swagger.
 * <ul>
 *   <li>описание, контакты, серверы local/prod;</li>
 *   <li>схема безопасности {@code bearerAuth} (JWT) — включает кнопку Authorize;</li>
 *   <li>две группы документов: {@code public} (всё, кроме {@code /api/v1/admin/**}) и
 *       {@code admin} (только {@code /api/v1/admin/**}). Префикс {@code /api/v1} добавляется
 *       маппингом (не context-path), поэтому в путях групп он присутствует явно.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Springdoc по умолчанию строит имена свойств схемы из имён полей Java (camelCase), игнорируя
     * глобальную стратегию Jackson. На проводе же JSON идёт в snake_case
     * ({@code spring.jackson.property-naming-strategy: SNAKE_CASE}), поэтому сгенерированные из
     * OpenAPI типы фронта расходились бы с реальным телом. Отдаём swagger-у ObjectMapper приложения,
     * чтобы имена в схеме совпадали с проводом.
     */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI tzevaBestOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tzeva Best API")
                        .version("v1")
                        .description("REST API интернет-магазина красок и услуг покраски Tzeva Best. "
                                + "Формат ошибок — RFC 9457 (application/problem+json). "
                                + "Аутентификация: access-токен в теле ответа (Bearer), refresh-токен — "
                                + "в httpOnly-куке (B-01); /auth/refresh и /auth/logout читают его из куки, "
                                + "тело с refresh_token — запасной путь за флагом app.auth.refresh-token-in-body.")
                        .contact(new Contact()
                                .name("Tzeva Best Dev")
                                .email("dev@tzevabest.co.il")
                                .url("https://tzevabest.co.il"))
                        .license(new License().name("Proprietary")))
                // Пути в документе уже содержат префикс /api/v1 (маппинг-префикс),
                // поэтому серверы указывают на корень хоста.
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("local"),
                        new Server().url("https://api.tzevabest.co.il").description("prod")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
}
