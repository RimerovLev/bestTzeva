package com.tzevabest.service.dto;

import java.time.Instant;

/** Внутренний комментарий (виден только персоналу). */
public record CommentView(long id, Long authorId, String body, Instant createdAt) {
}
