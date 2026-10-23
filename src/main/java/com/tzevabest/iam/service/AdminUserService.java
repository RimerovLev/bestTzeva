package com.tzevabest.iam.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.iam.dto.AssigneeView;
import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Административное управление пользователями. Смена роли — только ADMIN, с записью в audit_log. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final RefreshTokenStore refreshTokenStore;
    private final OneTimeTokenStore tokens;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notifications;
    private final MessageSource messages;
    private final JdbcTemplate jdbc;

    @Value("${app.web.admin-url}")
    private String adminUrl;

    /** Константный ключ транзакционного advisory-лока, сериализующего мутации состава ADMIN (п.12). */
    private static final long ADMIN_MUTATION_LOCK = 713_041L;

    /** Токен приглашения потребляется тем же сбросом пароля (см. PasswordService), поэтому namespace общий. */
    private static final String INVITE_NAMESPACE = "pwd-reset";
    private static final Duration INVITE_TTL = Duration.ofHours(48);

    /** Роли, у которых есть экраны заявок: на них и назначают. */
    private static final Set<UserRole> ASSIGNEE_ROLES = EnumSet.of(UserRole.SUPPORT, UserRole.MANAGER, UserRole.ADMIN);

    /** Служебные роли (не клиент) — состав, которым управляет экран «Сотрудники». */
    private static final Set<UserRole> STAFF_ROLES =
            EnumSet.of(UserRole.SUPPORT, UserRole.WAREHOUSE, UserRole.MANAGER, UserRole.ADMIN);

    /** Список сотрудников (любого статуса) для управления. Только ADMIN. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<com.tzevabest.iam.dto.EmployeeView> listEmployees() {
        return users.findByRoleInOrderByFirstNameAscLastNameAscIdAsc(STAFF_ROLES).stream()
                .map(u -> new com.tzevabest.iam.dto.EmployeeView(
                        u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getRole(), u.getStatus()))
                .toList();
    }

    /**
     * Кого можно назначить исполнителем заявки: активные сотрудники с доступом к заявкам. Имя — как
     * в учётке; пустое имя заменяется на email, чтобы строку в списке было с чем сопоставить.
     */
    @PreAuthorize("hasAnyRole('SUPPORT','MANAGER','ADMIN')")
    @Transactional(readOnly = true)
    public List<AssigneeView> assignees() {
        return users.findByStatusAndRoleInOrderByFirstNameAscLastNameAscIdAsc(UserStatus.ACTIVE, ASSIGNEE_ROLES).stream()
                .map(u -> new AssigneeView(u.getId(), displayName(u), u.getRole()))
                .toList();
    }

    private static String displayName(User u) {
        String name = Stream.of(u.getFirstName(), u.getLastName())
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(" "));
        return name.isBlank() ? (u.getEmail() != null ? u.getEmail() : "#" + u.getId()) : name;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void changeUserRole(long userId, UserRole newRole) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        UserRole oldRole = user.getRole();
        if (oldRole == newRole) {
            return; // нечего менять, лишних отзывов сессий не делаем
        }
        // Нельзя снять последнего активного ADMIN — иначе управлять ролями будет некому (аудит п.12).
        if (oldRole == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            lockAdminMutations();
            if (users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                throw new ConflictException("user.last_admin");
            }
        }
        user.setRole(newRole);

        auditService.record(currentUser.id(), currentUser.ip(), "user.role.changed",
                "user", userId, Map.of("from", oldRole.name(), "to", newRole.name()));
        // Понижение прав должно действовать сразу: отзываем сессии, иначе старый access-токен (до 15 мин)
        // работает со старой ролью (роль берётся из JWT). Аудит п.12.
        refreshTokenStore.revokeAllForUser(userId);
    }

    /**
     * Завести сотрудника (аудит п.3). Пароль не задаётся — пользователь создаётся в статусе PENDING,
     * на email уходит ссылка-приглашение с одноразовым токеном; по ней сотрудник задаёт пароль (тот же
     * путь, что и сброс), и аккаунт становится ACTIVE. Клиентов так не заводят (они регистрируются сами).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public long createEmployee(String email, String firstName, String lastName, UserRole role) {
        if (role == UserRole.CUSTOMER) {
            throw new ValidationException("user.role_not_staff");
        }
        // Нормализуем как AuthService.register (нижний регистр), иначе один адрес в разном регистре
        // создаст дубли и рассинхронит поиск по email.
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ConflictException("user.email_exists");
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);
        // Невходимый placeholder: реальный пароль сотрудник задаёт по ссылке-приглашению.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        users.save(user);

        sendInvite(user);
        auditService.record(currentUser.id(), currentUser.ip(), "user.created", "user", user.getId(),
                Map.of("role", role.name()));
        return user.getId();
    }

    /** Заблокировать сотрудника: вход закрыт (login требует ACTIVE) и активные сессии отзываются. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void block(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (user.getStatus() == UserStatus.BLOCKED) {
            return;
        }
        if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            lockAdminMutations();
            if (users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
                throw new ConflictException("user.last_admin");
            }
        }
        user.setStatus(UserStatus.BLOCKED);
        auditService.record(currentUser.id(), currentUser.ip(), "user.blocked", "user", userId, Map.of());
        refreshTokenStore.revokeAllForUser(userId);
    }

    /** Разблокировать сотрудника. Только из BLOCKED: приглашённого (PENDING) активирует установка пароля. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void unblock(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (user.getStatus() != UserStatus.BLOCKED) {
            return; // PENDING не трогаем — он станет ACTIVE, когда сотрудник задаст пароль по приглашению
        }
        user.setStatus(UserStatus.ACTIVE);
        auditService.record(currentUser.id(), currentUser.ip(), "user.unblocked", "user", userId, Map.of());
    }

    /**
     * Транзакционный advisory-лок: сериализует одновременные снятия/блокировки ADMIN, чтобы два
     * параллельных вызова не увидели оба {@code count = 2} и не оставили 0 администраторов (п.12).
     * Освобождается при коммите/откате транзакции.
     */
    private void lockAdminMutations() {
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> null, ADMIN_MUTATION_LOCK);
    }

    private void sendInvite(User user) {
        // Один активный токен на пользователя: повторное приглашение гасит прежнюю ссылку (аудит п.15).
        String token = tokens.issueSingle(INVITE_NAMESPACE, user.getId(), INVITE_TTL);
        String link = adminUrl + "/set-password?token=" + token;
        Locale locale = Locale.forLanguageTag(user.getLocale());
        String subject = messages.getMessage("email.staff_invite.subject", null, locale);
        String body = messages.getMessage("email.staff_invite.body", new Object[]{link}, locale);
        notifications.sendEmail(user.getEmail(), subject, body);
    }
}
