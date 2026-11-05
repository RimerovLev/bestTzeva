package com.tzevabest.ai.web;

import com.tzevabest.ai.ColorStudioService;
import com.tzevabest.ai.dto.RoomRenderResult;
import com.tzevabest.common.web.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * «Примерка цвета» (публичный): фото комнаты + цвет → OpenAI перекрашивает стены. Платный внешний
 * вызов, поэтому жёсткий лимит по IP (защита от накрутки счёта). Смену цвета витрина делает локально.
 */
@RestController
@RequestMapping("/color-studio")
@RequiredArgsConstructor
public class ColorStudioController {

    private final ColorStudioService service;

    @PostMapping(path = "/render", consumes = "multipart/form-data")
    @RateLimit(key = "ip", limit = 5, windowSeconds = 3600)
    public RoomRenderResult render(
            @RequestPart("file") MultipartFile file,
            @RequestParam("color") String color,
            @RequestParam(name = "name", required = false) String name) {
        return service.renderWalls(file, color, name);
    }
}
