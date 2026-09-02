package com.tzevabest.service.web;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.service.dto.PortfolioCard;
import com.tzevabest.service.dto.PortfolioProjectView;
import com.tzevabest.service.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Публичное портфолио выполненных работ. Только опубликованные проекты. */
@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService service;

    @GetMapping
    public PageResponse<PortfolioCard> list(
            @RequestParam(name = "property_type", required = false) PropertyType propertyType,
            @RequestParam(required = false) String city,
            @RequestParam(name = "work_type", required = false) WorkType workType,
            @RequestParam(required = false) String locale,
            @ApiPage(sort = {}) PageQuery page) {
        return service.list(propertyType, city, workType,
                locale != null ? locale : LocaleContextHolder.getLocale().getLanguage(), page);
    }

    @GetMapping("/{slug}")
    public PortfolioProjectView detail(@PathVariable String slug, @RequestParam(required = false) String locale) {
        return service.detail(slug, locale != null ? locale : LocaleContextHolder.getLocale().getLanguage());
    }
}
