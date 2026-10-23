package com.tzevabest.iam.service;

import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.BadRequestException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Восстановление и смена пароля. forgot всегда отвечает одинаково (не раскрываем существование
 * аккаунта). reset — одноразовый токен 1 час, при использовании отзываются все refresh-сессии.
 * change — из кабинета по текущему паролю (тоже отзывает сессии).
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final String NAMESPACE = "pwd-reset";
    private static final Duration TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final OneTimeTokenStore tokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshStore;
    private final NotificationSender notifications;
    private final MessageSource messages;

    // Ссылка сброса ведёт на экран админки (аккаунты — у персонала), а НЕ на базовый URL API (аудит п.2).
    @Value("${app.web.admin-url}")
    private String adminUrl;

    /** Ответ одинаков независимо от существования аккаунта. */
    public void forgot(String login) {
        findByLogin(login).ifPresent(user -> {
            // Один активный токен сброса на пользователя: прежние ссылки гасятся (аудит п.15).
            String token = tokens.issueSingle(NAMESPACE, user.getId(), TTL);
            String link = adminUrl + "/reset-password?token=" + token;
            Locale locale = Locale.forLanguageTag(user.getLocale());
            String subject = messages.getMessage("email.password_reset.subject", null, locale);
            if (user.getEmail() != null) {
                notifications.sendEmail(user.getEmail(), subject,
                        messages.getMessage("email.password_reset.body", new Object[]{link}, locale));
            } else if (user.getPhone() != null) {
                notifications.sendSms(user.getPhone(),
                        messages.getMessage("email.password_reset.sms", new Object[]{link}, locale));
            }
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        long userId = tokens.consume(NAMESPACE, token)
                .orElseThrow(() -> new BadRequestException("common.auth.reset_invalid"));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("common.auth.reset_invalid"));
        // Заблокированный не восстанавливает доступ через сброс пароля (аудит п.15).
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BadRequestException("common.auth.reset_invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Приглашённый сотрудник (PENDING) активируется, задав пароль по ссылке-приглашению (аудит п.3).
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }
        refreshStore.revokeAllForUser(userId); // все сессии инвалидируются
    }

    @Transactional
    public void change(long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("common.auth.password_mismatch");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshStore.revokeAllForUser(userId); // прочие сессии инвалидируются
    }

    private Optional<User> findByLogin(String login) {
        String value = login.trim();
        if (value.contains("@")) {
            return users.findByEmailIgnoreCase(value);
        }
        try {
            return users.findByPhone(PhoneNormalizer.normalize(value));
        } catch (ValidationException e) {
            return Optional.empty();
        }
    }
}
