package com.tzevabest.iam.domain;

/** Статус пользователя. Хранится как varchar (имя константы) с check-ограничением ck_users_status. */
public enum UserStatus {
    ACTIVE,
    PENDING,
    BLOCKED
}
