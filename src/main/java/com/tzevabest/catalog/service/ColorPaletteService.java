package com.tzevabest.catalog.service;

import com.tzevabest.catalog.domain.ColorSwatch;
import com.tzevabest.catalog.domain.ColorSystem;
import com.tzevabest.catalog.dto.ColorSwatchResponse;
import com.tzevabest.catalog.dto.ColorSwatchSaveRequest;
import com.tzevabest.catalog.repository.ColorSwatchRepository;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Справочник цветов (палитра) для витрины и админки. Публичное чтение — с локализацией имени
 * (подстановка HE), админский CRUD.
 */
@Service
@RequiredArgsConstructor
public class ColorPaletteService {

    private static final List<String> LOCALES = List.of("he", "ru", "en");

    private final ColorSwatchRepository swatches;
    private final JdbcTemplate jdbc;

    public List<ColorSwatchResponse> list(ColorSystem system, Boolean popular, String q, String localeParam) {
        String locale = resolveLocale(localeParam);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        args.add(locale); // для name coalesce
        if (system != null) {
            where.append(" and system = ?");
            args.add(system.name());
        }
        if (Boolean.TRUE.equals(popular)) {
            where.append(" and is_popular");
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (code ilike ? or name_translations->>'he' ilike ? or name_translations->>? ilike ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(locale);
            args.add(like);
        }
        String sql = "select id, code, system, hex, collection, is_popular, "
                + "coalesce(name_translations->>?, name_translations->>'he', code) as name "
                + "from color_swatches" + where + " order by system, code";
        // первый ? — locale для name; повторно в начало списка args уже добавлен как args[0]
        return jdbc.query(sql, (rs, n) -> new ColorSwatchResponse(
                rs.getLong("id"), rs.getString("code"), ColorSystem.valueOf(rs.getString("system")),
                rs.getString("hex"), rs.getString("name"), rs.getString("collection"),
                rs.getBoolean("is_popular")), args.toArray());
    }

    @Transactional
    public ColorSwatchResponse create(ColorSwatchSaveRequest request) {
        if (swatches.existsByCode(request.code())) {
            throw new ConflictException("catalog.color.code_taken");
        }
        ColorSwatch swatch = new ColorSwatch();
        apply(swatch, request);
        swatches.save(swatch);
        return toResponse(swatch);
    }

    @Transactional
    public ColorSwatchResponse update(long id, ColorSwatchSaveRequest request) {
        ColorSwatch swatch = swatches.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.color.not_found"));
        if (!swatch.getCode().equals(request.code()) && swatches.existsByCode(request.code())) {
            throw new ConflictException("catalog.color.code_taken");
        }
        apply(swatch, request);
        swatches.save(swatch);
        return toResponse(swatch);
    }

    @Transactional
    public void delete(long id) {
        ColorSwatch swatch = swatches.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.color.not_found"));
        swatches.delete(swatch);
    }

    private void apply(ColorSwatch swatch, ColorSwatchSaveRequest request) {
        swatch.setCode(request.code());
        swatch.setSystem(request.system());
        swatch.setHex(request.hex());
        swatch.setNameTranslations(request.nameTranslations());
        swatch.setCollection(request.collection());
        swatch.setPopular(request.popular() != null && request.popular());
    }

    private ColorSwatchResponse toResponse(ColorSwatch s) {
        String name = s.getNameTranslations() != null
                ? s.getNameTranslations().getOrDefault("he", s.getCode()) : s.getCode();
        return new ColorSwatchResponse(s.getId(), s.getCode(), s.getSystem(), s.getHex(),
                name, s.getCollection(), s.isPopular());
    }

    private String resolveLocale(String localeParam) {
        if (localeParam != null && LOCALES.contains(localeParam.toLowerCase(Locale.ROOT))) {
            return localeParam.toLowerCase(Locale.ROOT);
        }
        return "he";
    }
}
