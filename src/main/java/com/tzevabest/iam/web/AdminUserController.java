package com.tzevabest.iam.web;

import com.tzevabest.iam.dto.AssigneeView;
import com.tzevabest.iam.dto.ChangeRoleRequest;
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

    @PostMapping("/{id}/role")
    public void changeRole(@PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        adminUserService.changeUserRole(id, request.role());
    }
}
