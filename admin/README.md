# Tzeva Best — админка (admin)

Панель управления для персонала: каталог, склад, заявки, отчёты. Vite + React, TypeScript strict,
TanStack Router и Query. За аутентификацией, SEO не нужен (`noindex`).

## Запуск

```bash
corepack enable
pnpm install
cp .env.example .env.local   # в dev можно оставить пустым
pnpm dev                     # http://localhost:5173, /api проксируется на бэкенд
```

Бэкенд должен быть поднят на `http://localhost:8080` (Vite проксирует `/api` туда — тот же origin,
чтобы httpOnly refresh-кука долетала).

Скрипты: `pnpm dev`, `pnpm build`, `pnpm preview`, `pnpm typecheck`, `pnpm lint`, `pnpm test`,
`pnpm spec:pull`, `pnpm gen:api`.

## Аутентификация (модель B-01)

- **Access-токен — только в памяти вкладки** (`src/lib/api/tokens.ts`). В localStorage/sessionStorage
  ничего про аутентификацию: refresh живёт в httpOnly-куке, которую ставит сервер, фронт её не видит.
- **Старт вкладки** (`AuthProvider`): access-токена нет → сразу `POST /auth/refresh` по куке, показываем
  скелетон, а не форму входа. Поэтому перезагрузка не выкидывает из админки.
- **Перехват 401** (`src/lib/api/http.ts`): один общий refresh на все параллельные 401 (промис в
  модуле — single-flight), затем повтор исходного запроса ровно раз. Не удался — уводим на вход.
- **Вход** по email или телефону. Роли из `GET /auth/me`: CUSTOMER, SUPPORT, WAREHOUSE, MANAGER, ADMIN.

## Роли и разделы

`src/lib/auth/roles.ts` — единый список разделов и роли, которым они видны. Скрытие недоступных
разделов из меню — удобство, **не защита**: права проверяет сервер (403 — штатный ответ). Матрица
«роль → разделы» покрыта тестом (`roles.test.ts`): склад — WAREHOUSE+, каталог/цвета/отчёты —
MANAGER+, аудит и пользователи — только ADMIN.

## Типы API

Генерируются из OpenAPI бэкенда (группы `public` для auth и `admin` для админских эндпоинтов),
коммитятся. Пути групп не пересекаются — их типы объединяются в один клиент (`ApiPaths`).

```bash
pnpm spec:pull   # openapi/public.json + openapi/admin.json с бэкенда
pnpm gen:api     # src/lib/api/schema.public.ts + schema.admin.ts
```

CI гоняет `pnpm gen:api:check`: расхождение типов со спеком ломает сборку.

## RTL и локали

Иврит (RTL, основной) и русский. Направление и гарнитура — по `:lang()` (Heebo/Manrope), переключение
меняет `dir`/`lang` на `<html>`. Предпочтение языка — в localStorage (это не аутентификация). Таблицы:
порядок колонок зеркалит браузер, числа выравниваются «к концу строки» + `tabular-nums`.

## Общие компоненты

`src/components/ui`: `Button`, `Field` (с ошибкой поля из `errors`), `Modal`, `Skeleton`,
`DataTable` (сортировка + пагинация под PageResponse), `FilterPanel`. Свои, поверх токенов — без
UI-кита (он тянет свою систему тем и ломает RTL).

## Структура

```
openapi/                    спеки public + admin (источник типов)
src/
  main.tsx                  провайдеры + разводящий экран по статусу auth
  router.tsx                дерево маршрутов (code-based), заглушки разделов
  routes/                   LoginScreen, DashboardPage, SectionPage
  components/
    layout/                 Sidebar, Header, AdminLayout, LanguageToggle
    ui/                     Button, Field, Modal, Skeleton, DataTable, FilterPanel
  lib/
    api/                    schema.*.ts (СГЕНЕРИРОВАНО), http, session, tokens, problem, messages
    auth/                   AuthProvider, roles (+ матрица), тесты
    i18n/                   config, dictionaries, I18nProvider
  dictionaries/             he.json, ru.json
  styles/                   tokens.css, globals.css
```
