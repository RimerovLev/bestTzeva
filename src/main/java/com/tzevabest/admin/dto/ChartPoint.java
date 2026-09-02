package com.tzevabest.admin.dto;

import java.time.LocalDate;

/** Точка временного ряда: продуктовые и сервисные заявки за бакет. */
public record ChartPoint(LocalDate bucket, long productRequests, long serviceRequests) {
}
