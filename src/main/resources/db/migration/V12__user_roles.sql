-- Роли по спецификации T-3.2: CUSTOMER, SUPPORT, WAREHOUSE, MANAGER, ADMIN.
-- (Ранее ошибочно был SPECIALIST.) На пустой базе — просто новое check-ограничение.
alter table users drop constraint ck_users_role;
alter table users add constraint ck_users_role
    check (role in ('CUSTOMER', 'SUPPORT', 'WAREHOUSE', 'MANAGER', 'ADMIN'));
