package com.tzevabest.service.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.media.service.ContentTypeSniffer;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.media.service.StoredObject;
import com.tzevabest.service.dto.PortfolioAdminCard;
import com.tzevabest.service.dto.PortfolioAdminView;
import com.tzevabest.service.dto.PortfolioCreate;
import com.tzevabest.service.dto.PortfolioMediaResponse;
import com.tzevabest.service.dto.PortfolioTranslationInput;
import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Админ-управление портфолио (T-11.4): CRUD проектов и переводов, загрузка медиа с видом и парой,
 * публикация. Создание проекта из завершённой заявки подтягивает параметры объекта, но НЕ переносит
 * фото клиента — они в приватном бакете и требуют отдельного согласия. Изменения — в audit_log.
 */
@Service
@RequiredArgsConstructor
public class PortfolioAdminService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final MediaStorage mediaStorage;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @Transactional
    public long create(PortfolioCreate req) {
        requireHebrew(req);
        long id;
        try {
            id = jdbc.queryForObject("""
                    insert into portfolio_projects (slug, property_type, city, area_sqm, duration_days, completed_at)
                    values (?, ?, ?, ?, ?, ?) returning id""", Long.class,
                    req.slug(), req.propertyType() == null ? null : req.propertyType().name(), req.city(),
                    req.areaSqm(), req.durationDays(), req.completedAt());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("portfolio.slug_taken", req.slug());
        }
        saveWorkTypes(id, req.workTypes());
        saveTranslations(id, req);
        audit("portfolio.create", id, Map.of("slug", req.slug()));
        return id;
    }

    @Transactional
    public void update(long id, PortfolioCreate req) {
        requireExists(id);
        requireHebrew(req);
        // При смене slug старый адрес уходит в историю — для 301-редиректа (B-02).
        String oldSlug = jdbc.queryForObject("select slug from portfolio_projects where id = ?", String.class, id);
        if (oldSlug != null && !oldSlug.equals(req.slug())) {
            jdbc.update("insert into portfolio_slug_history (slug, project_id) values (?, ?) "
                            + "on conflict (slug) do update set project_id = excluded.project_id, created_at = now()",
                    oldSlug, id);
        }
        jdbc.update("""
                update portfolio_projects set slug = ?, property_type = ?, city = ?, area_sqm = ?,
                        duration_days = ?, completed_at = ?, updated_at = now() where id = ?""",
                req.slug(), req.propertyType() == null ? null : req.propertyType().name(), req.city(),
                req.areaSqm(), req.durationDays(), req.completedAt(), id);
        jdbc.update("delete from portfolio_project_work_types where project_id = ?", id);
        jdbc.update("delete from portfolio_translations where project_id = ?", id);
        saveWorkTypes(id, req.workTypes());
        saveTranslations(id, req);
        audit("portfolio.update", id, Map.of("slug", req.slug()));
    }

    @Transactional
    public void setPublished(long id, boolean published) {
        int updated = jdbc.update("update portfolio_projects set is_published = ?, updated_at = now() where id = ?",
                published, id);
        if (updated == 0) {
            throw new NotFoundException("portfolio.not_found");
        }
        audit(published ? "portfolio.publish" : "portfolio.unpublish", id, Map.of("published", published));
    }

    @Transactional
    public PortfolioMediaResponse uploadMedia(long id, MultipartFile file, String kind, String pairGroup,
                                              Integer sortOrder) {
        requireExists(id);
        if (!"BEFORE".equals(kind) && !"AFTER".equals(kind) && !"PROCESS".equals(kind)) {
            throw new ValidationException("portfolio.media_kind");
        }
        byte[] data = validate(file);
        // Тип — из содержимого (magic bytes), не со слов клиента: публичный бакет раздаёт inline,
        // поэтому SVG/HTML под видом картинки = stored XSS. Портфолио — только изображения. Аудит п.9.
        String safeType = ContentTypeSniffer.detect(data);
        if (safeType == null || !safeType.startsWith("image/")) {
            throw new ValidationException("portfolio.media_type");
        }
        // Медиа портфолио — маркетинговые, кладутся в ПУБЛИЧНЫЙ бакет.
        StoredObject obj = mediaStorage.put("portfolio/" + id, file.getOriginalFilename(), safeType, data);
        int order = sortOrder == null ? 0 : sortOrder;
        Long mediaId = jdbc.queryForObject("""
                insert into portfolio_media (project_id, url, kind, sort_order, pair_group)
                values (?, ?, ?, ?, ?) returning id""",
                Long.class, id, obj.url(), kind, order, blankToNull(pairGroup));
        audit("portfolio.media_upload", id, Map.of("kind", kind, "media_id", mediaId));
        return new PortfolioMediaResponse(mediaId, obj.url(), kind, blankToNull(pairGroup), order);
    }

    /**
     * Создать черновик проекта из завершённой заявки: подтягивает тип объекта, площадь, город и виды
     * работ. Фото клиента НЕ переносятся (приватный бакет, отдельное согласие). Проект не опубликован.
     */
    @Transactional
    public long createFromServiceRequest(long requestId) {
        Map<String, Object> r;
        try {
            r = jdbc.queryForMap("""
                    select request_number, property_type, area_sqm, city, status from service_requests where id = ?""",
                    requestId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new NotFoundException("service_request.not_found");
        }
        if (!"COMPLETED".equals(r.get("status"))) {
            throw new ConflictException("portfolio.request_not_completed");
        }
        String slug = ((String) r.get("request_number")).toLowerCase(java.util.Locale.ROOT)
                + "-" + UUID.randomUUID().toString().substring(0, 6);
        long id = jdbc.queryForObject("""
                insert into portfolio_projects (slug, property_type, city, area_sqm)
                values (?, ?, ?, ?) returning id""", Long.class,
                slug, r.get("property_type"), r.get("city"), r.get("area_sqm"));
        // Виды работ переносим; фото — нет.
        jdbc.update("""
                insert into portfolio_project_work_types (project_id, work_type)
                select ?, work_type from service_request_work_types where request_id = ?""", id, requestId);
        // HE-заголовок по умолчанию — номер заявки (менеджер отредактирует).
        jdbc.update("insert into portfolio_translations (project_id, locale, title) values (?, 'he', ?)",
                id, "פרויקט " + r.get("request_number"));
        audit("portfolio.create_from_request", id, Map.of("service_request_id", requestId));
        return id;
    }

    /** Список проектов для админки (включая неопубликованные). Заголовок — HE, иначе любой перевод. */
    @Transactional(readOnly = true)
    public List<PortfolioAdminCard> adminList() {
        return jdbc.query("""
                select p.id, p.slug,
                  (select t.title from portfolio_translations t where t.project_id = p.id
                     order by case t.locale when 'he' then 0 when 'ru' then 1 else 2 end limit 1) as title,
                  p.property_type, p.city, p.completed_at, p.is_published,
                  (select count(*) from portfolio_media m where m.project_id = p.id) as media_count
                from portfolio_projects p order by p.sort_order, p.id""",
                (rs, i) -> new PortfolioAdminCard(
                        rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                        propertyType(rs.getString("property_type")), rs.getString("city"),
                        rs.getObject("completed_at", LocalDate.class), rs.getBoolean("is_published"),
                        rs.getInt("media_count")));
    }

    /** Полные данные проекта для редактирования: все переводы и все медиа (с видом и парой). */
    @Transactional(readOnly = true)
    public PortfolioAdminView adminDetail(long id) {
        Object[] p = jdbc.query("""
                select slug, property_type, city, area_sqm, duration_days, completed_at, is_published
                from portfolio_projects where id = ?""",
                rs -> rs.next() ? new Object[]{
                        rs.getString("slug"), rs.getString("property_type"), rs.getString("city"),
                        rs.getBigDecimal("area_sqm"), rs.getObject("duration_days", Integer.class),
                        rs.getObject("completed_at", LocalDate.class), rs.getBoolean("is_published")
                } : null, id);
        if (p == null) {
            throw new NotFoundException("portfolio.not_found");
        }
        List<PortfolioTranslationInput> translations = jdbc.query("""
                select locale, title, description, meta_title, meta_description
                from portfolio_translations where project_id = ? order by locale""",
                (rs, i) -> new PortfolioTranslationInput(rs.getString("locale"), rs.getString("title"),
                        rs.getString("description"), rs.getString("meta_title"), rs.getString("meta_description")),
                id);
        List<PortfolioMediaResponse> media = jdbc.query("""
                select id, url, kind, pair_group, sort_order from portfolio_media
                where project_id = ? order by sort_order, id""",
                (rs, i) -> new PortfolioMediaResponse(rs.getLong("id"), rs.getString("url"), rs.getString("kind"),
                        rs.getString("pair_group"), rs.getInt("sort_order")),
                id);
        Set<WorkType> workTypes = EnumSet.noneOf(WorkType.class);
        for (String wt : jdbc.query("select work_type from portfolio_project_work_types where project_id = ?",
                (rs, i) -> rs.getString("work_type"), id)) {
            workTypes.add(WorkType.valueOf(wt));
        }
        return new PortfolioAdminView(id, (String) p[0], propertyType((String) p[1]), (String) p[2],
                (BigDecimal) p[3], (Integer) p[4], (LocalDate) p[5], workTypes, (Boolean) p[6],
                translations, media);
    }

    /** Удалить один медиа-файл проекта (строку БД; blob в публичном бакете остаётся — он без ключа). */
    @Transactional
    public void deleteMedia(long mediaId) {
        Long projectId = jdbc.query("select project_id from portfolio_media where id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, mediaId);
        if (projectId == null) {
            throw new NotFoundException("portfolio.media_not_found");
        }
        jdbc.update("delete from portfolio_media where id = ?", mediaId);
        audit("portfolio.media_delete", projectId, Map.of("media_id", mediaId));
    }

    /** Удалить проект со всеми переводами, медиа, видами работ и историей slug. */
    @Transactional
    public void delete(long id) {
        requireExists(id);
        jdbc.update("delete from portfolio_media where project_id = ?", id);
        jdbc.update("delete from portfolio_project_work_types where project_id = ?", id);
        jdbc.update("delete from portfolio_translations where project_id = ?", id);
        jdbc.update("delete from portfolio_slug_history where project_id = ?", id);
        jdbc.update("delete from portfolio_projects where id = ?", id);
        audit("portfolio.delete", id, Map.of());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static PropertyType propertyType(String name) {
        return name == null ? null : PropertyType.valueOf(name);
    }

    private void saveWorkTypes(long id, java.util.Set<WorkType> workTypes) {
        if (workTypes == null) {
            return;
        }
        for (WorkType wt : workTypes) {
            jdbc.update("insert into portfolio_project_work_types (project_id, work_type) values (?, ?)",
                    id, wt.name());
        }
    }

    private void saveTranslations(long id, PortfolioCreate req) {
        for (PortfolioTranslationInput t : req.translations()) {
            jdbc.update("""
                    insert into portfolio_translations (project_id, locale, title, description, meta_title, meta_description)
                    values (?, ?, ?, ?, ?, ?)""",
                    id, t.locale(), t.title(), t.description(), t.metaTitle(), t.metaDescription());
        }
    }

    private static void requireHebrew(PortfolioCreate req) {
        boolean hasHe = req.translations().stream().anyMatch(t -> "he".equals(t.locale()));
        if (!hasHe) {
            throw new ValidationException("portfolio.hebrew_required");
        }
    }

    private void requireExists(long id) {
        Integer c = jdbc.queryForObject("select count(*) from portfolio_projects where id = ?", Integer.class, id);
        if (c == null || c == 0) {
            throw new NotFoundException("portfolio.not_found");
        }
    }

    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("portfolio.media_empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("portfolio.media_too_large");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new ValidationException("portfolio.media_type");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("portfolio.media_unreadable");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private void audit(String action, long id, Map<String, Object> changes) {
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                action, "portfolio_project", id, changes);
    }
}
