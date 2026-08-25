package com.tzevabest.common.web;

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

    @Bean
    public OpenAPI tzevaBestOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tzeva Best API")
                        .version("v1")
                        .description("REST API интернет-магазина красок и услуг покраски Tzeva Best. "
                                + "Формат ошибок — RFC 9457 (application/problem+json).")
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
