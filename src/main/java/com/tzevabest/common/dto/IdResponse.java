package com.tzevabest.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Единый ответ на создание сущности — идентификатор созданного ресурса.
 *
 * @param id идентификатор
 */
public record IdResponse(@Schema(description = "Идентификатор ресурса", example = "42") long id) {

    public static IdResponse of(long id) {
        return new IdResponse(id);
    }
}
