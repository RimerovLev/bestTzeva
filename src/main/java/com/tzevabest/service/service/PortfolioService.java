package com.tzevabest.service.service;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.dto.MediaItem;
import com.tzevabest.service.dto.MediaPair;
import com.tzevabest.service.dto.PortfolioCard;
import com.tzevabest.service.dto.PortfolioProjectView;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Публичное чтение портфолио (T-11.4): список опубликованных проектов с фильтрами и карточка со
 * связанными парами «до/после». Неопубликованные наружу не отдаются.
 */
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final Set<String> LOCALES = Set.of("he", "ru", "en");

    private final JdbcTemplate jdbc;

    public PageResponse<PortfolioCard> list(PropertyType propertyType, String city, WorkType workType,
                                            String localeParam, PageQuery page) {
        String locale = resolveLocale(localeParam);
        StringBuilder where = new StringBuilder(" where p.is_published");
        List<Object> args = new ArrayList<>();
        if (propertyType != null) {
            where.append(" and p.property_type = ?");
            args.add(propertyType.name());
        }
        if (city != null && !city.isBlank()) {
            where.append(" and p.city ilike ?");
            args.add("%" + city.trim() + "%");
        }
        if (workType != null) {
            where.append(" and exists (select 1 from portfolio_project_work_types w "
                    + "where w.project_id = p.id and w.work_type = ?)");
            args.add(workType.name());
        }

        Long total = jdbc.queryForObject("select count(*) from portfolio_projects p" + where,
                Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        String titleExpr = "(select title from portfolio_translations t where t.project_id = p.id "
                + "order by case t.locale when ? then 0 when 'he' then 1 else 2 end limit 1)";
        String mainAfter = "(select url from portfolio_media m where m.project_id = p.id and m.kind = 'AFTER' "
                + "order by m.sort_order, m.id limit 1)";
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(locale);
        pageArgs.addAll(args);
        pageArgs.add(page.size());
        pageArgs.add((long) page.page() * page.size());
        List<PortfolioCard> items = jdbc.query("select p.id, p.slug, " + titleExpr + " as title, p.property_type, "
                        + "p.city, p.area_sqm, p.duration_days, p.completed_at, " + mainAfter + " as main_after "
                        + "from portfolio_projects p" + where + " order by p.sort_order, p.id limit ? offset ?",
                (rs, n) -> new PortfolioCard(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                        rs.getString("property_type") == null ? null : PropertyType.valueOf(rs.getString("property_type")),
                        rs.getString("city"), rs.getBigDecimal("area_sqm"), (Integer) rs.getObject("duration_days"),
                        rs.getObject("completed_at", java.time.LocalDate.class), rs.getString("main_after")),
                pageArgs.toArray());

        return PageResponse.of(items, page.page(), page.size(), totalElements);
    }

    public PortfolioProjectView detail(String slug, String localeParam) {
        String locale = resolveLocale(localeParam);
        long id;
        try {
            id = jdbc.queryForObject("select id from portfolio_projects where slug = ? and is_published",
                    Long.class, slug);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("portfolio.not_found");
        }

        String[] tr = jdbc.query("""
                select title, description, meta_title, meta_description from portfolio_translations
                where project_id = ? order by case locale when ? then 0 when 'he' then 1 else 2 end limit 1""",
                rs -> rs.next() ? new String[]{rs.getString("title"), rs.getString("description"),
                        rs.getString("meta_title"), rs.getString("meta_description")} : new String[4], id, locale);

        Map<String, Object> p = jdbc.queryForMap("""
                select property_type, city, area_sqm, duration_days, completed_at from portfolio_projects where id = ?""", id);

        Set<WorkType> workTypes = loadWorkTypes(id);
        List<MediaPair> pairs = new ArrayList<>();
        List<MediaItem> standalone = new ArrayList<>();
        buildMedia(id, pairs, standalone);

        return new PortfolioProjectView(id, slug, tr[0], tr[1], tr[2], tr[3],
                p.get("property_type") == null ? null : PropertyType.valueOf((String) p.get("property_type")),
                (String) p.get("city"), (java.math.BigDecimal) p.get("area_sqm"), (Integer) p.get("duration_days"),
                p.get("completed_at") == null ? null : ((java.sql.Date) p.get("completed_at")).toLocalDate(),
                workTypes, pairs, standalone);
    }

    private void buildMedia(long projectId, List<MediaPair> pairs, List<MediaItem> standalone) {
        // Пары «до/после» связаны pair_group; отдаём готовыми парами, не плоским списком.
        Map<String, String[]> byGroup = new LinkedHashMap<>(); // group -> [before, after]
        jdbc.query("select url, kind, sort_order, pair_group from portfolio_media where project_id = ? "
                        + "order by sort_order, id",
                rs -> {
                    String group = rs.getString("pair_group");
                    String kind = rs.getString("kind");
                    String url = rs.getString("url");
                    if (group != null && !group.isBlank()) {
                        String[] pair = byGroup.computeIfAbsent(group, k -> new String[2]);
                        if ("BEFORE".equals(kind)) {
                            pair[0] = url;
                        } else if ("AFTER".equals(kind)) {
                            pair[1] = url;
                        } else {
                            standalone.add(new MediaItem(url, kind, rs.getInt("sort_order")));
                        }
                    } else {
                        standalone.add(new MediaItem(url, kind, rs.getInt("sort_order")));
                    }
                }, projectId);
        byGroup.forEach((group, pair) -> pairs.add(new MediaPair(group, pair[0], pair[1])));
    }

    private Set<WorkType> loadWorkTypes(long id) {
        List<WorkType> list = jdbc.query("select work_type from portfolio_project_work_types where project_id = ?",
                (rs, n) -> WorkType.valueOf(rs.getString("work_type")), id);
        return list.isEmpty() ? EnumSet.noneOf(WorkType.class) : EnumSet.copyOf(list);
    }

    private static String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return "he";
    }
}
