package com.tzevabest.catalog.web;

import com.tzevabest.catalog.domain.ColorSystem;
import com.tzevabest.catalog.dto.ColorSwatchResponse;
import com.tzevabest.catalog.service.ColorPaletteService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Публичная палитра цветов. Имя локализовано (?locale / Accept-Language, дефолт HE). */
@RestController
@RequestMapping("/colors")
@RequiredArgsConstructor
public class ColorController {

    private final ColorPaletteService palette;

    @GetMapping
    public List<ColorSwatchResponse> list(@RequestParam(required = false) ColorSystem system,
                                          @RequestParam(required = false) Boolean popular,
                                          @RequestParam(name = "q", required = false) String query,
                                          @RequestParam(required = false) String locale) {
        return palette.list(system, popular, query,
                locale != null ? locale : LocaleContextHolder.getLocale().getLanguage());
    }
}
