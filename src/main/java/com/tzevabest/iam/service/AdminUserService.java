package com.tzevabest.iam.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Административное управление пользователями. Смена роли — только ADMIN, с записью в audit_log. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final AuditService auditService;
    private final CurrentUser currentUser;

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
