package com.tzevabest.common.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Пароль: >= 8 символов, хотя бы одна буква и одна цифра. */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null
                && value.length() >= 8
                && LETTER.matcher(value).find()
                && DIGIT.matcher(value).find();
    }
}
