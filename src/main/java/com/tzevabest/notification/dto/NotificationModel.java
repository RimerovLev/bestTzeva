package com.tzevabest.notification.dto;

/**
 * Строго типизированная модель подстановки под шаблон: свой record на каждый шаблон, чтобы
 * отсутствующее поле ловилось на компиляции, а не в письме клиенту. Поля record в порядке объявления
 * дают позиционные аргументы {0}, {1}, … для MessageFormat в бандлах.
 */
public interface NotificationModel {

    /** Аргументы для MessageFormat в порядке объявления полей record. */
    default Object[] args() {
        var components = getClass().getRecordComponents();
        if (components == null) {
            return new Object[0];
        }
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            try {
                args[i] = components[i].getAccessor().invoke(this);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read notification model field", e);
            }
        }
        return args;
    }
}
