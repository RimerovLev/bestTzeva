package com.tzevabest.inventory.dto;

import com.tzevabest.inventory.domain.ReferenceType;

/**
 * Контекст складского движения: кто (actor) и по какому поводу (reference). Пишется в
 * {@code inventory_movements}, чтобы журнал отвечал на вопрос «кто и зачем двигал остаток».
 *
 * @param referenceType источник движения; {@code null} трактуется как {@link ReferenceType#MANUAL}
 * @param referenceId   идентификатор источника (напр. id импорта); может быть {@code null}
 * @param actorId       пользователь, инициировавший операцию; {@code null} для системных
 */
public record MovementContext(ReferenceType referenceType, Long referenceId, Long actorId) {

    /** Ручная операция администратора без внешней ссылки. */
    public static MovementContext manual(Long actorId) {
        return new MovementContext(ReferenceType.MANUAL, null, actorId);
    }

    /** Тип ссылки с подстановкой MANUAL по умолчанию. */
    public ReferenceType typeOrManual() {
        return referenceType == null ? ReferenceType.MANUAL : referenceType;
    }
}
