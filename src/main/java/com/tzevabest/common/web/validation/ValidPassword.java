package com.tzevabest.common.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Политика пароля: минимум 8 символов, хотя бы одна буква и одна цифра.
 * Сообщение локализуется через MessageSource (ключ {@code common.password.weak}).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordConstraintValidator.class)
public @interface ValidPassword {
    String message() default "{common.password.weak}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
