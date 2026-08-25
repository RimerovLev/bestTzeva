package com.tzevabest.iam.web;

import com.tzevabest.iam.dto.ChangeRoleRequest;
import com.tzevabest.iam.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Управление пользователями (админка). Смена роли — только ADMIN (проверка на сервисе). */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{id}/role")
    public void changeRole(@PathVariable long id, @Valid @RequestBody ChangeRoleRequest request) {
        adminUserService.changeUserRole(id, request.role());
    }
}
