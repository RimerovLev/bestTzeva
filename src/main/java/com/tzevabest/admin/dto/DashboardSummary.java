package com.tzevabest.admin.dto;

/** Сводка дашборда. change_pct — изменение к предыдущему периоду той же длины, %. */
public record DashboardSummary(
        long productRequestsToday, long serviceRequestsToday,
        long productRequestsPeriod, long productRequestsPrevPeriod, Double productChangePct,
        long serviceRequestsPeriod, long serviceRequestsPrevPeriod, Double serviceChangePct,
        long lowStockPositions, long reservedPositions,
        double productConversion, double serviceConversion) {
}
