package com.tzevabest.common.web.error;

/**
 * Ресурс запрошен по устаревшему slug'у. Не ошибка, а сигнал к постоянному редиректу (301) на
 * актуальный адрес — обрабатывается в {@code GlobalExceptionHandler}, чтобы старые ссылки и
 * поисковая выдача не теряли вес после переименования (B-02).
 */
public class SlugMovedException extends RuntimeException {

    private final String location;

    public SlugMovedException(String location) {
        super("slug moved to " + location);
        this.location = location;
    }

    public String getLocation() {
        return location;
    }
}
