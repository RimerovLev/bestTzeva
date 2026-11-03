package com.tzevabest.content.web;

import com.tzevabest.content.service.SiteImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Публичные картинки сайта: карта slot→url. Витрина берёт отсюда, иначе дефолт из сборки. */
@RestController
@RequestMapping("/site-images")
@RequiredArgsConstructor
public class SiteImageController {

    private final SiteImageService service;

    @GetMapping
    public Map<String, String> all() {
        return service.publicMap();
    }
}
