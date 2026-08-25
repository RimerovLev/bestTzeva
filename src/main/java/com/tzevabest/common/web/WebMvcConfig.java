package com.tzevabest.common.web;

import com.tzevabest.common.web.convert.StringToEnumConverterFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Общая web-MVC настройка REST API: резолвер параметров пагинации {@link ApiPageArgumentResolver},
 * строковый конвертер enum {@link StringToEnumConverterFactory} и регистрация сквозного
 * фильтра идентификатора запроса {@link RequestIdFilter}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Префикс версии API. Применяется только к нашим @RestController (пакет
     * {@code com.tzevabest}), поэтому actuator, swagger-ui и собственные контроллеры
     * springdoc (/api-docs) остаются в корне, а рядом можно поднять /api/v2.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Только контроллеры нашего базового пакета. HandlerTypePredicate комбинирует
        // селекторы через OR, поэтому добавлять сюда annotation(@RestController) нельзя —
        // тогда префикс поймал бы и собственные @RestController springdoc (/api-docs).
        configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forBasePackage("com.tzevabest"));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ApiPageArgumentResolver());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumConverterFactory());
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        // Раньше всех остальных фильтров, чтобы trace_id был доступен по всей цепочке.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
