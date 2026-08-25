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
import io.jsonwebtoken.JwtException;
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
            throw invalidCredentials();
        }
        return issueTokens(user, userAgent, ip);
    }

    public TokenResponse refresh(String refreshToken, String userAgent, String ip) {
        JwtService.RefreshClaims claims = parseRefresh(refreshToken);
        if (!refreshStore.validateAndRotate(claims.userId(), claims.tokenId(), refreshToken)) {
            throw refreshInvalid();
        }
        User user = users.findById(claims.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::refreshInvalid);
        return issueTokens(user, userAgent, ip);
    }

    public void logout(long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshStore.revokeAllForUser(userId);
            return;
        }
        try {
            JwtService.RefreshClaims claims = jwt.parseRefresh(refreshToken);
            refreshStore.revoke(claims.userId(), claims.tokenId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // некорректный токен при logout — молча игнорируем (идемпотентно)
        }
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

    private BusinessException refreshInvalid() {
        return new BusinessException("common.auth.refresh_invalid", HttpStatus.UNAUTHORIZED);
    }
}
