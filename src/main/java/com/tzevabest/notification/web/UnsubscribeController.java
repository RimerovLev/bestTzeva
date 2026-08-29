package com.tzevabest.notification.web;

import com.tzevabest.common.web.ratelimit.ClientIpResolver;
import com.tzevabest.notification.service.UnsubscribeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Публичная отписка от рассылок по подписанному токену из письма. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeService unsubscribe;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestParam String token, HttpServletRequest http) {
        unsubscribe.unsubscribe(token, clientIpResolver.resolve(http));
        return Map.of("unsubscribed", true);
    }
}
