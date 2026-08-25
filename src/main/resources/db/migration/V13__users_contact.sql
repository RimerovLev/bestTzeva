-- Регистрация по email ИЛИ по телефону (норма для израильского рынка).
-- email становится nullable, при этом должен быть заполнен хотя бы один контакт.
-- Уникальный индекс ux_users_email_lower продолжает работать: NULL уникальности не мешает.
alter table users alter column email drop not null;
alter table users add constraint ck_users_contact
    check (email is not null or phone is not null);
