package com.tzevabest.iam.service;

import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.iam.dto.LoginRequest;
import com.tzevabest.iam.dto.MeResponse;
import com.tzevabest.iam.dto.RegisterRequest;
import com.tzevabest.iam.dto.TokenResponse;
import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import io.jsonwebtoken.JwtException;
import org.springframework.context.MessageSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Аутентификация пользователей магазина: регистрация, вход (email/телефон), ротация refresh,
 * logout, профиль. Вход даёт одинаковый ответ и время при неверном пароле и отсутствующем
 * пользователе (защита от перебора базы).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("he", "ru", "en");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenStore refreshStore;
    private final AccountLockService accountLock;
    private final NotificationSender notifications;
    private final MessageSource messages;

    @Value("${app.auth.default-locale:he}")
    private String defaultLocale;

    /** Захешированная заглушка для константного времени при отсутствии пользователя. */
    private String dummyHash;

    @PostConstruct
    void init() {
        this.dummyHash = passwordEncoder.encode("constant-time-placeholder");
    }

    @Transactional
    public TokenResponse register(RegisterRequest request, String userAgent, String ip) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (email == null && phone == null) {
            throw new ValidationException("common.auth.contact_required");
        }
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("common.auth.contact_taken");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw new ConflictException("common.auth.contact_taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLocale(resolveLocale(request.locale()));
        users.save(user);

        return issueTokens(user, userAgent, ip);
    }

    public TokenResponse login(LoginRequest request, String userAgent, String ip) {
        User user = findByLogin(request.login()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash); // одинаковое время
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getStatus() != UserStatus.ACTIVE) {
            // Считаем неудачу для уведомления владельца о подозрительной активности.
            // Троттлинг ответа (429) и защита от перебора — на @RateLimit по строке логина и по IP,
            // а не здесь: проверка по userId сработала бы только для существующих аккаунтов и стала бы
            // оракулом их существования (см. R-04).
            accountLock.recordFailure(user.getId());
            throw invalidCredentials();
        }
        accountLock.reset(user.getId());
        return issueTokens(user, userAgent, ip);
    }

    public TokenResponse refresh(String refreshToken, String userAgent, String ip) {
        JwtService.RefreshClaims claims = parseRefresh(refreshToken);
        if (refreshStore.validateAndRotate(claims.userId(), claims.tokenId(), refreshToken)) {
            User user = users.findById(claims.userId())
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(this::refreshInvalid);
            TokenResponse tokens = issueTokens(user, userAgent, ip);
            // Запоминаем выданную пару под старый tokenId на окно снисхождения (R-06).
            refreshStore.rememberRotation(claims.userId(), claims.tokenId(),
                    tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
            return tokens;
        }
        // Ротация не прошла. Внутри окна снисхождения повтор того же токена — это гонка клиента
        // (две параллельные вкладки): возвращаем ту же новую пару, не считая кражей (R-06).
        RefreshTokenStore.Rotation graced = refreshStore.replayRotation(claims.userId(), claims.tokenId());
        if (graced != null) {
            return TokenResponse.bearer(graced.accessToken(), graced.refreshToken(), graced.expiresIn());
        }
        // За пределами окна: токен корректно подписан и не истёк, но его нет ни в хранилище, ни в
        // grace — предъявлен уже отработавший refresh. Легитимный клиент старый токен не хранит,
        // поэтому это признак кражи: гасим все сессии и уведомляем владельца (R-05).
        handleRefreshReuse(claims.userId());
        throw refreshInvalid();
    }

    /** Выход из одной сессии: отзывается ровно предъявленный refresh-токен. Массовый отзыв — {@link #logoutAll}. */
    public void logout(long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return; // токен обязателен на уровне контроллера; без него — no-op, без массового отзыва
        }
        try {
            JwtService.RefreshClaims claims = jwt.parseRefresh(refreshToken);
            // Отзываем только собственный токен: чужой refresh (claims.userId != userId)
            // молча игнорируем, иначе A, предъявив токен B, погасил бы сессию B. Тихий no-op
            // (а не 401) выбран сознательно: logout идемпотентен и не должен служить оракулом
            // принадлежности токена.
            if (claims.userId() == userId) {
                refreshStore.revoke(userId, claims.tokenId());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // некорректный токен при logout — молча игнорируем (идемпотентно)
        }
    }

    /** Явный массовый выход: гасит все сессии пользователя (напр. «выйти на всех устройствах»). */
    public void logoutAll(long userId) {
        refreshStore.revokeAllForUser(userId);
    }

    public MeResponse me(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        return new MeResponse(user.getId(), user.getEmail(), user.getPhone(),
                user.getFirstName(), user.getLastName(), user.getRole(), user.getLocale());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private TokenResponse issueTokens(User user, String userAgent, String ip) {
        String access = jwt.generateAccess(user.getId(), user.getRole().name(), user.getEmail(), user.getLocale());
        JwtService.GeneratedRefresh refresh = jwt.generateRefresh(user.getId());
        refreshStore.issue(user.getId(), refresh.tokenId(), refresh.token(), userAgent, ip, jwt.refreshTtl());
        return TokenResponse.bearer(access, refresh.token(), jwt.accessTtl().toSeconds());
    }

    private Optional<User> findByLogin(String login) {
        String value = login.trim();
        if (value.contains("@")) {
            return users.findByEmailIgnoreCase(value);
        }
        try {
            return users.findByPhone(PhoneNormalizer.normalize(value));
        } catch (ValidationException e) {
            // некорректный телефон при логине — как «пользователь не найден» (не раскрываем)
            return Optional.empty();
        }
    }

    private JwtService.RefreshClaims parseRefresh(String refreshToken) {
        try {
            return jwt.parseRefresh(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw refreshInvalid();
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return PhoneNormalizer.normalize(phone);
    }

    private String resolveLocale(String locale) {
        return (locale != null && SUPPORTED_LOCALES.contains(locale)) ? locale : defaultLocale;
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("common.auth.invalid_credentials", HttpStatus.UNAUTHORIZED);
    }

    /** Реакция на повторное использование refresh: отзыв всех сессий и уведомление владельцу (R-05). */
    private void handleRefreshReuse(long userId) {
        refreshStore.revokeAllForUser(userId);
        users.findById(userId).ifPresent(user -> {
            Locale locale = Locale.forLanguageTag(
                    (user.getLocale() == null || user.getLocale().isBlank()) ? "he" : user.getLocale());
            String subject = messages.getMessage("notification.token_replay.subject", null, locale);
            String body = messages.getMessage("notification.token_replay.body", null, locale);
            if (user.getEmail() != null) {
                notifications.sendEmail(user.getEmail(), subject, body);
            } else if (user.getPhone() != null) {
                notifications.sendSms(user.getPhone(), body);
            }
        });
    }

    private BusinessException refreshInvalid() {
        return new BusinessException("common.auth.refresh_invalid", HttpStatus.UNAUTHORIZED);
    }
}
