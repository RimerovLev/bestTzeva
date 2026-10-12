package com.tzevabest.iam.dto;

import com.tzevabest.iam.domain.UserRole;

/**
 * Сотрудник, на которого можно назначить заявку. Только то, что нужно для выбора и подписи в списке:
 * ни контактов, ни статуса учётки наружу не отдаём.
 */
public record AssigneeView(Long id, String name, UserRole role) {
}
