# E2E, доступность, скриншоты, Lighthouse (F-11.1)

Проверки фронтенда против **настоящего бэкенда**, не против моков. Стек изолирован от рабочего:

| что     | порт  | откуда                                                          |
|---------|-------|-----------------------------------------------------------------|
| бэкенд  | 18080 | `build/libs/*.jar`, база `tzevabest_e2e` (пересоздаётся), Redis db 1 |
| витрина | 3100  | `next build` в `web/.next-e2e` + `next start`                   |
| админка | 5174  | `vite build` + `vite preview`, `/api` → :18080                  |

Postgres, Redis и MinIO — общие контейнеры из `docker-compose.yml`. Рабочая база, `next dev` (3000) и
`vite` (5173) не трогаются.

## Запуск

```bash
cd e2e && pnpm install && pnpm exec playwright install chromium
pnpm stack:up            # сборка всего + запуск; E2E_GRADLE_FLAGS=--offline без сети
pnpm test                # сброс базы и Redis (~20 с) + все тесты, кроме скриншотов на macOS
pnpm test:docker         # то же в Linux-образе Playwright — как в CI, со скриншотами
pnpm lhci                # Lighthouse CI по порогам
pnpm stack:down
```

Повторный прогон без пересборки: `E2E_SKIP_BUILD=1 pnpm stack:up`. Логи стека — `bash scripts/stack.sh logs`.

## Что проверяется

- **setup** (`tests/seed.setup.ts`) — сид через admin API: категория, товар `E2E-VELVET` с двумя
  вариантами, склад `E2E-MAIN`, стартовый остаток 2. Учётка админа создаётся `AdminBootstrap` в пустой
  базе (значения — в `env.ts`, только для одноразовой e2e-базы).
- **flows**
  - `flows.spec.ts` — гость собирает заявку на витрине → сотрудник входит в админку, проводит приход
    и резервирует позиции именно этой заявки; остатки сверяются по колонкам склада.
  - `measure.spec.ts` — форма замера в 4 шага с загрузкой фото в MinIO по presigned-ссылке.
  - `a11y.spec.ts` — axe (WCAG 2.1 A/AA) на 8 страницах витрины в обеих локалях и 6 экранах админки;
    клавиатура и видимый фокус на формах и фильтрах. `E2E_A11Y_DUMP=файл` — собрать все нарушения
    разом вместо падения на первом.
- **visual** (`visual.spec.ts`) — скриншоты ключевых страниц на иврите и русском (RTL ломается в
  стрелках, отступах, таблицах). Анимации выключены через `prefers-reduced-motion`, WebGL-холсты скрыты.
- **Lighthouse CI** (`lighthouserc.cjs`) — мобильный профиль, медиана из 5: производительность ≥ 85,
  доступность ≥ 95, SEO = 100 на индексируемых страницах.

## Скриншотные эталоны

Эталоны (`tests/__screenshots__`) снимаются **только в Linux-образе** Playwright: на macOS шрифты и
сглаживание другие, и эталон с Mac в CI не совпадёт. Поэтому на macOS проект `visual` без Docker
не запускается.

Переснять после намеренного изменения вёрстки:

```bash
bash scripts/in-docker.sh --project=visual --update-snapshots
```

или в GitHub Actions: workflow «E2E, a11y, Lighthouse» → Run workflow → `update_snapshots`, затем
закоммитить артефакт `screenshots`. Перед коммитом эталоны просматриваются глазами.
