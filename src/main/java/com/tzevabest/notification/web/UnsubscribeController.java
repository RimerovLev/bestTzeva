package com.tzevabest.notification.web;

import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.notification.service.UnsubscribeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Публичная отписка от рассылок по непрозрачному токену из письма.
 * <p>
 * GET состояние НЕ меняет (R-63): почтовые сканеры (Outlook Safe Links, антивирусные прокси) заранее
 * открывают ссылки из писем — GET-отписка отписывала бы получателей без единого клика. GET лишь
 * проверяет токен и возвращает подтверждение; фактическая отписка — POST по кнопке. Для клиентов с
 * одношаговой отпиской (RFC 8058, заголовок {@code List-Unsubscribe-Post}) тот же POST срабатывает
 * без страницы подтверждения — сканеры по нему не ходят.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeService unsubscribe;
    private final ClientIpResolver clientIpResolver;

    /** Подтверждение: токен проверяется (невалидный → 422), состояние не меняется. */
    @GetMapping("/unsubscribe")
    public Map<String, Object> confirm(@RequestParam String token) {
        unsubscribe.validate(token);
        return Map.of("action", "unsubscribe", "confirm_required", true);
    }

    /** Фактическая отписка (кнопка подтверждения или one-click POST почтового клиента). */
    @PostMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestParam String token, HttpServletRequest http) {
        unsubscribe.unsubscribe(token, clientIpResolver.resolve(http));
        return Map.of("unsubscribed", true);
    }
}
