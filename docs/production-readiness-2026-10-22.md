# Production-readiness audit — 2026-10-22

Аудит технического долга, безопасности и готовности к продакшену. Срез по ветке
`feature/frontend-storefront` (бэкенд Spring Boot + витрина Next.js + админка Vite/React + e2e).
Общий вердикт: **кодовая база близка к продакшену**, критичных находок нет; найденные дыры в
конфигурации прод-секретов закрыты в этом проходе.

## Что проверено и в порядке

- **Секреты в репозитории.** `dev-accounts.env.local` в `.gitignore`; трекаемых `.env`/секретов нет.
- **SQL-инъекции.** Динамический SQL (списки, фильтры, сортировки) собирается из параметров `?`;
  имена колонок в `order by` и `product_media` — из whitelist-наборов (`SORTABLE`/`SORT_COLUMNS`),
  не из пользовательского ввода.
- **CORS.** Явный список origin из конфигурации (в prod — `CORS_ALLOWED_ORIGINS` без дефолта),
  `allowCredentials=true` только с явными origin — не с `*`.
- **Аутентификация.** Rate limiting на login/refresh/reset/OTP (по IP и по логину, с учётом неудач);
  append-only аудит; маскирование телефона/email в аудите и логах (logback `%replace`).
- **Actuator.** В prod наружу только `health,info`; Swagger выключен; health-details
  `when-authorized`; liveness/readiness-пробы включены (healthcheck в Dockerfile валиден).
- **Контейнер.** Многоступенчатый Dockerfile, непривилегированный пользователь, container-aware JVM,
  HEALTHCHECK по readiness-пробе.
- **Гигиена кода.** 2 TODO в бэкенде, 0 во фронтендах; ни одного `@Disabled`-теста, `console.log`,
  `debugger`. Бэкенд-тесты зелёные (486, включая новый guard); миграции V1…V49 только добавляют.

## Исправлено в этом проходе

| # | Severity | Находка | Фикс |
|---|---|---|---|
| 1 | **HIGH** | В `application-prod.yml` обязательны только `JWT_SECRET` и notification-секреты. `SPAM_SIGNING_SECRET` и `PRIVACY_PSEUDONYM_SECRET` не переопределены → в prod молча берут публичные `dev-only`-дефолты из `application.yml` (подделка токенов антиспам-формы; предсказуемые псевдонимы анонимизации). | Оба объявлены в prod без дефолта (`${SPAM_SIGNING_SECRET}`, `${PRIVACY_PSEUDONYM_SECRET}`). |
| 2 | **MEDIUM** | Нет подстраховки: забытая env-переменная с dev-дефолтом подняла бы prod с небезопасным секретом молча. | `ProdSecretsGuard` (профиль `prod`) на старте отвергает любой секрет с маркером `dev-only`/`insecure`/`change-me`, пустой или короче 24 симв. — приложение не стартует. |
| 3 | **MEDIUM** | Витрина Next.js не отдавала заголовки безопасности. | `next.config.ts`: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Strict-Transport-Security`. |
| 4 | **LOW** | `deployment.md` ссылался на устаревший `NOTIFICATION_SIGNING_SECRET` (разделён ранее), не перечислял новые обязательные секреты; `.env.example` не содержал notification/spam/privacy-секретов; «миграции V1…V46». | Актуализированы перечень обязательных секретов, `.env.example`, диапазон миграций (V1…V49). |

## Чек-лист перед выкатом (вне среды разработки)

Требует реального окружения/прогона — не закрывается кодом:

- [ ] **Секреты в Secrets Manager/SSM**: `JWT_SECRET`, `NOTIFICATION_WEBHOOK_SECRET`,
      `NOTIFICATION_UNSUBSCRIBE_SECRET`, `SPAM_SIGNING_SECRET`, `PRIVACY_PSEUDONYM_SECRET` — длинные
      случайные; `ProdSecretsGuard` проверит на старте.
- [ ] **CSP**: заголовок Content-Security-Policy задать на прокси/CDN витрины (источники Plausible и
      изображений — под конкретный деплой; поэтому не зашит в `next.config`).
- [ ] **Скан зависимостей** (бэкенд Gradle + фронтенд npm) на CVE — добавить шаг в CI, если ещё нет.
- [ ] **Прогон e2e** (`e2e/`) и фронтенд-сборок (`web-ci`, `admin-ci`) на зелёном против stage.
- [ ] **Нагрузочный прогон** (`perf/k6`) и проверка алертов (`docs/alerts.md`).
- [ ] **Учебное восстановление** из бэкапа (`ops/backup`, `docs/disaster-recovery.md`).
- [ ] **Flyway в prod**: `SPRING_FLYWAY_ENABLED=false` у приложения, миграции — отдельным CI-шагом.
- [ ] **Ротация dev-аккаунтов** перед передачей (`dev-accounts.env.local`).

## Рекомендации (не блокеры)

- Prometheus в prod закрыт (exposure `health,info`) — если нужен скрейп метрик, вынести
  `/actuator/prometheus` на отдельный защищённый порт/сеть, а не открывать публично.
- Рассмотреть автоматический dependency-scan (Dependabot/`gradle dependencyCheck`, `npm audit`) как
  регулярный, а не разовый шаг.
