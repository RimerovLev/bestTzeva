package com.tzevabest.ai.web;

import com.tzevabest.ai.ColorStudioService;
import com.tzevabest.ai.TurnstileVerifier;
import com.tzevabest.ai.dto.RoomRenderResult;
import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.common.web.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * «Примерка цвета» (публичный): фото комнаты + цвет → OpenAI перекрашивает стены. Платный внешний
 * вызов, поэтому три рубежа: капча Turnstile (человечность), лимит по IP (перебор) и общий потолок
 * на эндпоинт (бюджет). Смену цвета витрина делает локально, без новых вызовов.
 */
@RestController
@RequestMapping("/color-studio")
@RequiredArgsConstructor
public class ColorStudioController {

    private final ColorStudioService service;
    private final TurnstileVerifier turnstile;
    private final ClientIpResolver clientIp;

    @PostMapping(path = "/render", consumes = "multipart/form-data")
    // Два барьера частоты: на один IP (защита от перебора) и общий потолок на эндпоинт (защита бюджета
    // OpenAI от ботнета с ротацией IP — его 5/час/IP не ограничивают). Капча — ниже, до вызова OpenAI.
    @RateLimit(key = "ip", limit = 5, windowSeconds = 3600)
    @RateLimit(key = "global", limit = 60, windowSeconds = 3600)
    public RoomRenderResult render(
            @RequestPart("file") MultipartFile file,
            @RequestParam("color") String color,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "captcha", required = false) String captcha,
            HttpServletRequest request) {
        // Проверка токена до платного вызова. Выключенная капча (нет secret) пропускает всех.
        turnstile.verify(captcha, clientIp.resolve(request));
        return service.renderWalls(file, color, name);
    }
}
