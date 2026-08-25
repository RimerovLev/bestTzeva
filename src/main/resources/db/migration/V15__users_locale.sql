-- Предпочитаемая локаль пользователя (для JWT-claim locale и уведомлений). Дефолт he.
alter table users add column locale varchar(5) not null default 'he';
alter table users add constraint ck_users_locale check (locale in ('he', 'ru', 'en'));
