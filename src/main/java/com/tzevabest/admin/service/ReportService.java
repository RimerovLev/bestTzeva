package com.tzevabest.admin.service;

import com.tzevabest.admin.dto.Report;
import com.tzevabest.admin.dto.TopItem;
import com.tzevabest.service.dto.ServiceRequestMetrics;
import com.tzevabest.service.service.ServiceRequestAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Отчёты админки (T-13.2). Показатели спроса переиспользуют агрегаты дашборда (T-13.1) — цифры
 * обязаны сходиться. Заголовки локализуются (иврит — RTL в XLSX). Тяжёлые отчёты (>3 мес) и чтение с
 * реплики — точки расширения (конфигурация), в ядре синхронно.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DashboardService dashboard;
    private final ServiceRequestAdminService serviceRequests;
    private final MessageSource messages;
    private final JdbcTemplate jdbc;

    public Report build(String type, LocalDate from, LocalDate to, String localeParam) {
        Locale locale = Locale.forLanguageTag(resolveLocale(localeParam));
        return switch (type) {
            case "demand" -> demand(from, to, locale);
            case "services" -> services(locale);
            case "warehouse" -> warehouse(locale);
            case "contacts" -> contacts(locale);
            default -> throw new com.tzevabest.common.web.error.ValidationException("report.unknown_type", type);
        };
    }

    private Report demand(LocalDate from, LocalDate to, Locale locale) {
        List<List<Object>> rows = new ArrayList<>();
        for (TopItem c : dashboard.topColors(from, to, 1000)) { // те же цифры, что на дашборде
            rows.add(List.of(c.key(), c.quantity()));
        }
        return new Report(msg("report.demand.title", locale),
                List.of(msg("report.header.color", locale), msg("report.header.units", locale)), rows);
    }

    private Report services(Locale locale) {
        ServiceRequestMetrics m = serviceRequests.metrics();
        List<List<Object>> rows = new ArrayList<>();
        m.byStatus().forEach((status, count) -> rows.add(List.of(status, count)));
        return new Report(msg("report.services.title", locale),
                List.of(msg("report.header.status", locale), msg("report.header.count", locale)), rows);
    }

    private Report warehouse(Locale locale) {
        // Позиции без движения более 90 дней (по журналу движений; таблицу inventory не трогаем).
        List<List<Object>> rows = jdbc.query("""
                select v.sku sku, coalesce(max(m.created_at)::date::text, '-') last_move
                from product_variants v
                left join inventory_movements m on m.variant_id = v.id
                where v.is_active
                group by v.sku
                having max(m.created_at) is null or max(m.created_at) < now() - interval '90 days'
                order by v.sku""",
                (rs, n) -> List.<Object>of(rs.getString("sku"), rs.getString("last_move")));
        return new Report(msg("report.warehouse.title", locale),
                List.of(msg("report.header.sku", locale), msg("report.header.last_move", locale)), rows);
    }

    private Report contacts(Locale locale) {
        // Распределение обращений по источникам (обе формы заявок).
        List<List<Object>> rows = jdbc.query("""
                select source, sum(c) c from (
                    select source, count(*) c from product_requests group by source
                    union all
                    select source, count(*) c from service_requests group by source
                ) t group by source order by c desc""",
                (rs, n) -> List.<Object>of(rs.getString("source"), rs.getLong("c")));
        return new Report(msg("report.contacts.title", locale),
                List.of(msg("report.header.source", locale), msg("report.header.count", locale)), rows);
    }

    private String msg(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private static String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (Map.of("he", 1, "ru", 1, "en", 1).containsKey(lower)) {
                return lower;
            }
        }
        return "he";
    }
}
