package com.tzevabest.admin.service;

import com.tzevabest.admin.dto.ChartPoint;
import com.tzevabest.admin.dto.DashboardSummary;
import com.tzevabest.admin.dto.RecentRequestItem;
import com.tzevabest.admin.dto.TopItem;
import com.tzevabest.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Метрики дашборда (T-13.1). Считаются одним SQL с агрегацией на стороне БД (условные агрегаты
 * FILTER вместо нескольких запросов на таблицу — по духу R-48). Заявки в статусах CANCELLED/LOST
 * исключаются из показателей спроса (топы), но учитываются в конверсии (знаменатель — все заявки).
 * Результаты кэшируются на 5 минут (см. DashboardCacheConfig), инвалидация по расписанию.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId TZ = ZoneId.of("Asia/Jerusalem");

    private final JdbcTemplate jdbc;
    private final InventoryService inventory;

    @Cacheable(value = "dashboard", key = "'summary:' + #from + ':' + #to")
    public DashboardSummary summary(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now(TZ).minusDays(30) : from;
        LocalDate t = to == null ? LocalDate.now(TZ) : to;
        long days = java.time.temporal.ChronoUnit.DAYS.between(f, t) + 1;
        Timestamp periodStart = ts(f);
        Timestamp periodEnd = ts(t.plusDays(1));
        Timestamp prevStart = ts(f.minusDays(days));
        Timestamp todayStart = ts(LocalDate.now(TZ));

        long[] pr = counts("product_requests", periodStart, periodEnd, prevStart, todayStart);
        long[] sr = counts("service_requests", periodStart, periodEnd, prevStart, todayStart);

        // Остаток — через InventoryService (прямой SQL к inventory только внутри его модуля).
        long lowStock = inventory.countLowStock();
        Long reserved = jdbc.queryForObject(
                "select count(*) from stock_reservations where status = 'ACTIVE'", Long.class);

        return new DashboardSummary(
                pr[3], sr[3],
                pr[0], pr[1], changePct(pr[0], pr[1]),
                sr[0], sr[1], changePct(sr[0], sr[1]),
                lowStock, reserved == null ? 0 : reserved,
                conversion(pr[2], pr[4]), conversion(sr[2], sr[4]));
    }

    /** [period, prev, completed, today, total] одним запросом на таблицу через FILTER. */
    private long[] counts(String table, Timestamp periodStart, Timestamp periodEnd, Timestamp prevStart,
                          Timestamp todayStart) {
        return jdbc.queryForObject("select "
                        + "count(*) filter (where created_at >= ? and created_at < ?) period, "
                        + "count(*) filter (where created_at >= ? and created_at < ?) prev, "
                        + "count(*) filter (where status = 'COMPLETED') completed, "
                        + "count(*) filter (where created_at >= ?) today, "
                        + "count(*) total from " + table,
                (rs, n) -> new long[]{rs.getLong("period"), rs.getLong("prev"), rs.getLong("completed"),
                        rs.getLong("today"), rs.getLong("total")},
                periodStart, periodEnd, prevStart, periodStart, todayStart);
    }

    @Cacheable(value = "dashboard", key = "'chart:' + #from + ':' + #to + ':' + #granularity")
    public List<ChartPoint> requestsChart(LocalDate from, LocalDate to, String granularity) {
        LocalDate f = from == null ? LocalDate.now(TZ).minusDays(30) : from;
        LocalDate t = to == null ? LocalDate.now(TZ) : to;
        String unit = switch (granularity == null ? "day" : granularity.toLowerCase()) {
            case "week" -> "week";
            case "month" -> "month";
            default -> "day";
        };
        String step = "1 " + unit;
        // Пропущенные бакеты заполняются нулями через generate_series (график не рвётся).
        return jdbc.query("""
                select b::date bucket,
                       coalesce(pr.c, 0) product, coalesce(sr.c, 0) service
                from generate_series(date_trunc(?, ?::timestamptz), ?::timestamptz, (?)::interval) b
                left join (select date_trunc(?, created_at) bb, count(*) c from product_requests
                           where created_at >= date_trunc(?, ?::timestamptz) and created_at < ?::timestamptz group by 1) pr
                       on pr.bb = b
                left join (select date_trunc(?, created_at) bb, count(*) c from service_requests
                           where created_at >= date_trunc(?, ?::timestamptz) and created_at < ?::timestamptz group by 1) sr
                       on sr.bb = b
                order by b""",
                (rs, n) -> new ChartPoint(rs.getObject("bucket", LocalDate.class),
                        rs.getLong("product"), rs.getLong("service")),
                unit, ts(f), ts(t.plusDays(1)), step,
                unit, unit, ts(f), ts(t.plusDays(1)),
                unit, unit, ts(f), ts(t.plusDays(1)));
    }

    @Cacheable(value = "dashboard", key = "'colors:' + #from + ':' + #to + ':' + #limit")
    public List<TopItem> topColors(LocalDate from, LocalDate to, int limit) {
        // Спрос: количество запрошенных единиц по цвету, без отменённых/потерянных заявок.
        return jdbc.query("""
                select i.color_snapshot key, sum(i.qty) qty
                from product_request_items i join product_requests r on r.id = i.request_id
                where r.status not in ('CANCELLED','LOST') and i.color_snapshot is not null
                    and (?::timestamptz is null or r.created_at >= ?) and (?::timestamptz is null or r.created_at < ?)
                group by i.color_snapshot order by qty desc, key limit ?""",
                (rs, n) -> new TopItem(rs.getString("key"), rs.getString("key"), rs.getLong("qty")),
                bound(from), tsOrNull(from), bound(to), tsOrNull(to == null ? null : to.plusDays(1)), limit);
    }

    @Cacheable(value = "dashboard", key = "'products:' + #from + ':' + #to + ':' + #limit")
    public List<TopItem> topProducts(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("""
                select p.id key, p.sku label, sum(i.qty) qty
                from product_request_items i
                join product_requests r on r.id = i.request_id
                join product_variants v on v.id = i.variant_id
                join products p on p.id = v.product_id
                where r.status not in ('CANCELLED','LOST')
                    and (?::timestamptz is null or r.created_at >= ?) and (?::timestamptz is null or r.created_at < ?)
                group by p.id, p.sku order by qty desc, key limit ?""",
                (rs, n) -> new TopItem(rs.getString("key"), rs.getString("label"), rs.getLong("qty")),
                bound(from), tsOrNull(from), bound(to), tsOrNull(to == null ? null : to.plusDays(1)), limit);
    }

    @Cacheable(value = "dashboard", key = "'categories:' + #from + ':' + #to + ':' + #limit")
    public List<TopItem> topCategories(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("""
                select c.id key, c.slug label, sum(i.qty) qty
                from product_request_items i
                join product_requests r on r.id = i.request_id
                join product_variants v on v.id = i.variant_id
                join products p on p.id = v.product_id
                join categories c on c.id = p.category_id
                where r.status not in ('CANCELLED','LOST')
                    and (?::timestamptz is null or r.created_at >= ?) and (?::timestamptz is null or r.created_at < ?)
                group by c.id, c.slug order by qty desc, key limit ?""",
                (rs, n) -> new TopItem(rs.getString("key"), rs.getString("label"), rs.getLong("qty")),
                bound(from), tsOrNull(from), bound(to), tsOrNull(to == null ? null : to.plusDays(1)), limit);
    }

    @Cacheable(value = "dashboard", key = "'recent:' + #limit")
    public List<RecentRequestItem> recentRequests(int limit) {
        List<RecentRequestItem> all = new ArrayList<>();
        all.addAll(jdbc.query("select request_number, customer_name, status, created_at "
                        + "from product_requests order by created_at desc limit ?",
                (rs, n) -> new RecentRequestItem("product", rs.getString("request_number"),
                        rs.getString("customer_name"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()), limit));
        all.addAll(jdbc.query("select request_number, customer_name, status, created_at "
                        + "from service_requests order by created_at desc limit ?",
                (rs, n) -> new RecentRequestItem("service", rs.getString("request_number"),
                        rs.getString("customer_name"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()), limit));
        all.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Double changePct(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : null; // рост с нуля не выражается процентом
        }
        return Math.round((current - previous) * 10000.0 / previous) / 100.0;
    }

    private static double conversion(long completed, long total) {
        return total == 0 ? 0.0 : Math.round(completed * 10000.0 / total) / 100.0;
    }

    private static Timestamp ts(LocalDate d) {
        return Timestamp.from(d.atStartOfDay(TZ).toInstant());
    }

    private static Timestamp tsOrNull(LocalDate d) {
        return d == null ? null : ts(d);
    }

    /** Флаг «граница задана» для условия (? is null or ...) — передаём саму дату как маркер. */
    private static Timestamp bound(LocalDate d) {
        return tsOrNull(d);
    }
}
