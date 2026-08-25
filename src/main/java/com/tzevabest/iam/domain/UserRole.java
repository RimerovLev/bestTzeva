package com.tzevabest.iam.domain;

/** Роль пользователя. Хранится как varchar (имя константы) с check-ограничением ck_users_role. */
public enum UserRole {
    CUSTOMER,
    SUPPORT,
    WAREHOUSE,
    MANAGER,
    ADMIN
}
