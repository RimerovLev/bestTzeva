package com.tzevabest.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Помечает параметр контроллера типа {@link PageQuery} для разбора query-параметров
 * {@code page}, {@code size}, {@code sort} общим резолвером {@link ApiPageArgumentResolver}.
 * Дефолт размера — 20, максимум — 100 (превышение обрезается, а не ошибка).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiPage {

    /**
     * Allow-list полей, по которым эндпоинту разрешена сортировка. Сортировка по полю
     * вне списка — ошибка 400. Пустой список означает, что сортировка не поддерживается
     * (любой {@code sort} → 400), чтобы клиент не сортировал по произвольным полям.
     */
    String[] sort() default {};
}
