package com.tzevabest.admin.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Помечает метод админ-сервиса для автоматического аудита (T-13.3): аспект пишет автора, IP,
 * действие, тип и id сущности и аргументы (без чувствительных полей) в audit_log.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Тип сущности (entity_type). */
    String entity();

    /** Действие; по умолчанию — {@code <entity>.<methodName>}. */
    String action() default "";
}
