package com.tzevabest.notification.web;

import com.tzevabest.notification.service.NotificationWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичный приём вебхуков доставки от провайдеров. Аутентификация — по подписи тела (HMAC), не по
 * токену; защита от повторов внутри сервиса.
 */
@RestController
@RequestMapping("/webhooks/notifications")
@RequiredArgsConstructor
public class NotificationWebhookController {

    private final NotificationWebhookService webhooks;

    @PostMapping("/{provider}")
    public void receive(@PathVariable String provider,
                        @RequestBody String rawBody,
                        @RequestHeader(name = "X-Signature", required = false) String signature) {
        webhooks.handle(provider, rawBody, signature);
    }
}
