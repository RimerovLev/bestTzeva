package com.tzevabest.request.dto;

import com.tzevabest.request.domain.ProductRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Полная карточка заявки: позиции со снимками и текущим состоянием каталога, история, комментарии. */
public record ProductRequestDetail(
        long id,
        String requestNumber,
        ProductRequestStatus status,
        String customerName,
        String customerPhone,
        String customerEmail,
        String comment,
        String preferredContactTime,
        Long assignedManagerId,
        Instant createdAt,
        BigDecimal indicativeTotal,
        List<Item> items,
        List<HistoryEntry> history,
        List<CommentEntry> comments) {

    /** Позиция: снимок на момент подачи + текущее состояние каталога рядом. */
    public record Item(long variantId, int qty,
                       String snapshotName, String snapshotSku, String snapshotColor,
                       BigDecimal snapshotVolume, BigDecimal snapshotPrice,
                       BigDecimal currentPrice, Integer currentAvailable, Boolean currentActive) {
    }

    public record HistoryEntry(String fromStatus, String toStatus, Long changedBy, String comment, Instant createdAt) {
    }

    public record CommentEntry(Long authorId, String text, Instant createdAt) {
    }
}
