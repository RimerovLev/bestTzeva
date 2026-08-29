package com.tzevabest.service.dto;

import java.util.List;

/** Список presigned-загрузок. */
public record PresignResponse(List<PresignedUploadView> uploads) {
}
