package com.tzevabest.common.web;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Инфраструктура локализации ответов об ошибках.
 * <p>
 * Язык определяется по заголовку {@code Accept-Language}; поддерживаются he/ru/en,
 * дефолт — {@code he} (обязательный язык проекта). Сообщения берутся из бандлов
 * {@code messages_*.properties} (UTF-8).
 */
@Configuration
public class WebErrorConfig {

    private static final Locale HE = Locale.forLanguageTag("he");
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Locale EN = Locale.forLanguageTag("en");

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        // Неизвестная локаль не должна проваливаться в системную — используем базовый бандл.
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(false);
        return ms;
    }

    /**
     * Валидатор бинов, берущий сообщения из нашего {@link MessageSource} — чтобы ключи
     * вида {@code {common.password.weak}} локализовались по Accept-Language, а не из
     * дефолтного ValidationMessages.
     */
    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(HE);
        resolver.setSupportedLocales(List.of(HE, RU, EN));
        return resolver;
    }
}
