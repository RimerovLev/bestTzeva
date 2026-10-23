package com.tzevabest.iam.service;

import com.tzevabest.common.web.error.BadRequestException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Подтверждение email: одноразовый токен 24 часа (хеш в Redis). */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String NAMESPACE = "email-verify";
    private static final Duration TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final OneTimeTokenStore tokens;
    private final NotificationSender notifications;
    private final org.springframework.context.MessageSource messages;

    // Ссылка ведёт на страницу витрины (там POST), а не на GET API (аудит п.14): GET-подтверждение
    // срабатывало бы на сканерах ссылок, «съедая» одноразовый токен.
    @Value("${app.web.storefront-url:http://localhost:3000}")
    private String storefrontUrl;

    public void sendVerification(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (user.getEmail() == null) {
            throw new BadRequestException("common.auth.no_email");
        }
        String token = tokens.issueSingle(NAMESPACE, userId, TTL);
        String link = storefrontUrl + "/verify-email?token=" + token;
        java.util.Locale locale = java.util.Locale.forLanguageTag(user.getLocale());
        notifications.sendEmail(user.getEmail(),
                messages.getMessage("email.verify.subject", null, locale),
                messages.getMessage("email.verify.body", new Object[]{link}, locale));
    }

    @Transactional
    public void verify(String token) {
        long userId = tokens.consume(NAMESPACE, token)
                .orElseThrow(() -> new BadRequestException("common.auth.verification_invalid"));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("common.auth.verification_invalid"));
        user.setEmailVerifiedAt(Instant.now());
    }
}
