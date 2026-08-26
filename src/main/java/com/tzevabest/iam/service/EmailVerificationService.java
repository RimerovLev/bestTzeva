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

    @Value("${app.web.base-url}")
    private String baseUrl;

    public void sendVerification(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (user.getEmail() == null) {
            throw new BadRequestException("common.auth.no_email");
        }
        String token = tokens.issue(NAMESPACE, userId, TTL);
        String link = baseUrl + "/api/v1/auth/email/verify?token=" + token;
        notifications.sendEmail(user.getEmail(), "Tzeva Best — verify your email",
                "Confirm your email: " + link);
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
