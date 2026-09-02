-- ─────────────────────────────────────────────────────────────────────────────
-- T-11.4: портфолио. SEO-поля переводов (как в каталоге) и флаг согласия клиента
-- на публикацию фото его объекта (фото из приватного бакета не переносятся
-- автоматически — нужно отдельное согласие).
-- ─────────────────────────────────────────────────────────────────────────────

alter table portfolio_translations
    add column meta_title       varchar(255),
    add column meta_description varchar(500);

alter table service_requests
    add column portfolio_publish_consent boolean not null default false;
