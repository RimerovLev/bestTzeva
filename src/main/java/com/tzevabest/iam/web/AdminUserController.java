package com.tzevabest.iam.web;

import com.tzevabest.iam.dto.AssigneeView;
import com.tzevabest.iam.dto.ChangeRoleRequest;
import com.tzevabest.iam.dto.CreateEmployeeRequest;
import com.tzevabest.iam.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Управление пользователями (админка). Смена роли — только ADMIN; список исполнителей — персонал заявок. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** Исполнители для назначения заявок (id, имя, роль) — SUPPORT/MANAGER/ADMIN. */
    @GetMapping("/assignees")
    public List<AssigneeView> assignees() {
        return adminUserService.assignees();
    }

    /** Список сотрудников для управления (ADMIN). */
    @GetMapping
    public List<com.tzevabest.iam.dto.EmployeeView> list() {
        return adminUserService.listEmployees();
    }

    @PostMapping("/{id}/role")
    public void changeRole(@PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        adminUserService.changeUserRole(id, request.role());
    }

    /** Завести сотрудника (ADMIN). Пароль задаётся по ссылке-приглашению на email. */
    @PostMapping
    public Map<String, Long> create(@Valid @RequestBody CreateEmployeeRequest request) {
        long id = adminUserService.createEmployee(request.email(), request.firstName(),
                request.lastName(), request.role());
        return Map.of("id", id);
    }

    /** Заблокировать сотрудника (ADMIN): вход закрыт, сессии отозваны. */
    @PostMapping("/{id}/block")
    public void block(@PathVariable long id) {
        adminUserService.block(id);
    }

    /** Разблокировать сотрудника (ADMIN). */
    @PostMapping("/{id}/unblock")
    public void unblock(@PathVariable long id) {
        adminUserService.unblock(id);
    }
}
