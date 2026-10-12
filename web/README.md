# Tzeva Best — витрина (web)

Публичная витрина: каталог красок (сбор лидов) и услуги покраски. Next.js 15 (App Router),
TypeScript strict, SSR ради индексации названий красок и услуг.

## Запуск

```bash
corepack enable
pnpm install
cp .env.example .env.local
pnpm dev
```

Открыть <http://localhost:3000> — редирект на локаль (`/he` по умолчанию).

Скрипты: `pnpm dev`, `pnpm build`, `pnpm start`, `pnpm lint`, `pnpm typecheck`.

## Решения

### Локализация в пути

Локаль — сегмент пути (`/he/...`, `/ru/...`), не кука: нужны отдельные индексируемые адреса
на каждый язык. Дефолт — иврит (RTL). i18n заложена на три локали, наполняются he и ru;
en добавляется позже без переделок. Тексты — только в словарях (`src/dictionaries`), в
разметке строк на иврите/русском нет.

Корневой `app/layout.tsx` — сквозной; `<html dir lang>` рендерит `app/[locale]/layout.tsx`,
потому что направление известно только там.

### Стили: Tailwind, а не CSS Modules

Выбран **Tailwind v3.4**. Причина — RTL: логические утилиты (`ms-*`, `me-*`, `ps-*`, `pe-*`,
`start-*`, `end-*`) и вариант `rtl:` дают одну таблицу стилей, работающую в обе стороны без
зеркалящих переопределений. С CSS Modules те же логические свойства пришлось бы выписывать
руками в каждом модуле. UI-кит не подключается: кнопка, поле, селект, модалка и скелетон
пишутся свои поверх токенов.

Все цвета, радиусы, отступы и размеры шрифта — CSS-переменные из `src/styles/tokens.css`
(единственный источник, зафиксирован в `docs/design-system.md`). В `tailwind.config.ts`
утилиты ссылаются на эти переменные; значений по месту в компонентах нет.

### Клиент API: типы из OpenAPI

Типы API **не пишутся руками** — генерируются из OpenAPI-спецификации бэкенда и коммитятся.

```bash
pnpm spec:pull   # обновить openapi/public.json с работающего бэкенда (localhost:8080)
pnpm gen:api     # перегенерировать src/lib/api/schema.ts из закоммиченного спека
```

`openapi/public.json` и `src/lib/api/schema.ts` коммитятся. CI (`.github/workflows/web-ci.yml`)
гоняет `pnpm gen:api:check`: если спек обновили, а типы не перегенерировали, сборка падает —
расхождение контракта ловится на сборке, а не в рантайме.

Вызовы идут через `createApiClient(locale)` (`src/lib/api/http.ts`) — тонкая обёртка над
`openapi-fetch` поверх сгенерированных типов:

- базовый URL из `NEXT_PUBLIC_API_BASE_URL`;
- `Accept-Language` из локали и клиентский `X-Request-Id` в каждом запросе;
- таймаут через `AbortSignal.timeout`;
- `unwrap()` разворачивает ответ: данные при успехе, `ApiError` при ошибке.

Ошибки — RFC 9457 (`application/problem+json`): `ProblemDetail { code, detail, errors, trace_id }`
превращается в `ApiError`. Текст пользователю берётся по `code` из словаря (`dictionaries/*.json`,
ключ `errors`), серверный `detail` — только как запас. `trace_id` логируется в консоль и
показывается мелким шрифтом рядом с сообщением: по нему поддержка находит запрос в логах.

Один модуль на сервер и клиент: в серверных компонентах — `fetch` с `revalidate` через тот же
клиент, на клиенте — TanStack Query (`src/app/providers.tsx`), который переиспользует `ApiError`
для политики повторов (4xx не повторяет, 429 — с оглядкой на `Retry-After`).

### Шрифты

`next/font` (self-host, subset, `display=swap`) — нет CLS и нет обращений к Google на
рантайме. Семейство переключается по `:lang()`: Heebo (иврит), Manrope (кириллица),
Frank Ruhl Libre (дисплейная), IBM Plex Mono (данные). Одна интерфейсная гарнитура на обе
локали не годится — ивритские шрифты Google не несут кириллицу.

## Структура

```
openapi/public.json       спецификация бэкенда (источник для генерации типов)
src/
  app/
    layout.tsx            сквозной корневой layout
    providers.tsx         TanStack Query provider (клиентский)
    [locale]/
      layout.tsx          <html dir lang>, провайдеры, шапка и подвал
      page.tsx            главная (заглушка)
  components/layout/       Header, Footer, LocaleSwitcher
  dictionaries/           he.json, ru.json (в т.ч. тексты ошибок по code)
  lib/
    api/
      schema.ts           СГЕНЕРИРОВАНО из openapi/public.json — руками не править
      http.ts             createApiClient, unwrap, таймаут, заголовки
      problem.ts          ProblemDetail, ApiError
      messages.ts         текст ошибки по code + поля валидации
    fonts.ts              next/font → CSS-переменные
    i18n/                 config (локали, dir), загрузка словарей
  styles/
    tokens.css            дизайн-токены
    globals.css           Tailwind + :lang() + база RTL
  middleware.ts           префикс локали для адресов без него
```

## TODO (ждёт данных клиента)

- Телефон, номер компании (ח.פ.), адрес и часы работы в подвале — сейчас плейсхолдеры.
