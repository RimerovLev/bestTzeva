package com.tzevabest.iam.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.iam.dto.AssigneeView;
import com.tzevabest.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Административное управление пользователями. Смена роли — только ADMIN, с записью в audit_log. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    /** Роли, у которых есть экраны заявок: на них и назначают. */
    private static final Set<UserRole> ASSIGNEE_ROLES = EnumSet.of(UserRole.SUPPORT, UserRole.MANAGER, UserRole.ADMIN);

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
        user.setRole(newRole);

        auditService.record(currentUser.id(), currentUser.ip(), "user.role.changed",
                "user", userId, Map.of("from", oldRole.name(), "to", newRole.name()));
    }
}
