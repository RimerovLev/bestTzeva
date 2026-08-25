# Соглашения по схеме БД — Tzeva Best (PostgreSQL 16)

Схема меняется **только** через Flyway-миграции (`src/main/resources/db/migration`).
`spring.jpa.hibernate.ddl-auto=validate` — Hibernate ничего не создаёт и не меняет,
только сверяет маппинг со схемой.

## Именование

| Объект            | Правило                                              | Пример                          |
|-------------------|------------------------------------------------------|---------------------------------|
| Таблица           | множественное число, snake_case                      | `users`, `user_addresses`       |
| Первичный ключ    | всегда `id bigint generated always as identity`      | `id`                            |
| Внешний ключ      | явное имя `fk_<таблица>_<поле>`                       | `fk_user_addresses_user_id`     |
| Уникальность      | `uq_<таблица>_<поле(я)>`                              | `uq_users_phone`                |
| Check-ограничение | `ck_<таблица>_<поле>`                                 | `ck_users_role`                 |
| Индекс            | `ix_<таблица>_<поле(я)>` (обычный), `ux_…` (unique)  | `ix_user_addresses_user_id`     |
| Триггер           | `trg_<таблица>_<действие>`                            | `trg_users_set_updated_at`      |

## Типы

- **Время** — `timestamptz` (в БД UTC). На всех таблицах с бизнес-смыслом обязательны
  `created_at` и `updated_at` (оба `timestamptz not null default now()`).
- **Деньги** — `numeric(12,2)`, валюта хранится отдельным полем (ILS).
- **Enum** — хранится как `varchar` с `check`-ограничением, **не** как postgres enum type
  (проще эволюционировать без ALTER TYPE). В БД лежит имя Java-константы в верхнем регистре
  (Hibernate `@Enumerated(STRING)`); в JSON API значение отдаётся в нижнем регистре.
- **Строки** — `varchar(n)` с осмысленной длиной; `text` — для заведомо длинных полей.

## Время обновления

Общая триггерная функция `set_updated_at()` проставляет `updated_at = now()` в `BEFORE UPDATE`.
Триггер вешается на каждую таблицу с `updated_at` — так поле корректно и при обновлении
напрямую через SQL, а не только через приложение. В сущностях `created_at`/`updated_at`
помечены `@Generated` (пишет и читает их БД).

## Идемпотентность и неизменяемость миграций

- Версия миграции задаётся именем файла `V<n>__<описание>.sql`; после мержа файл
  **не редактируется** — исправления идут новой миграцией.
- Расширения включаются через `create extension if not exists`.

## Первичные ключи и идентичность

- `id bigint generated always as identity` — приложение не подставляет `id` вручную
  (Hibernate `GenerationType.IDENTITY`).
