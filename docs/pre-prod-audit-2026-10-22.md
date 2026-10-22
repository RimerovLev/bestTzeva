# Pre-prod аудит: безопасность, баги, косяки — 2026-10-22

Срез: ветка `feature/frontend-storefront`, коммит `bf2032b`. Охват: бэкенд (Spring Boot), витрина
(`web/`, Next.js), админка (`admin/`, Vite/React), CI/CD (`.github/workflows`), Dockerfile, конфиги.
Это второй, независимый проход после [production-readiness-2026-10-22.md](production-readiness-2026-10-22.md):
то, что там уже закрыто (обязательные prod-секреты, `ProdSecretsGuard`, заголовки витрины), здесь не
повторяется.

**Вердикт: в прод пока рано.** Код бэкенда в целом сделан аккуратно (см. «Что в порядке»), но есть
5 блокеров и красная бэкенд-сборка (п. 6): небезопасный fail-open при незаданном профиле, неработающий сброс пароля, отсутствие
способа завести и заблокировать сотрудника, деплой-конвейер из заглушек и уязвимые зависимости.

## Сводка

| # | Важность | Область | Находка |
|---|---|---|---|
| 1 | 🔴 CRITICAL* | Конфиг / безопасность | Без `SPRING_PROFILES_ACTIVE=prod` приложение стартует в `local`: API открыт, JWT подписан публичным dev-секретом → любой выпускает себе токен ADMIN |
| 2 | 🟠 HIGH | Баг | Сброс пароля не работает: ссылка ведёт на несуществующую страницу, в prod — на `localhost` |
| 3 | 🟠 HIGH | Функционал / доступ | В prod нельзя завести сотрудника и нельзя заблокировать уволенного |
| 4 | 🟠 HIGH | CI/CD | Деплой, миграции и smoke-тесты в `main.yml` — `echo`-заглушки; для витрины и админки нет образа/деплоя |
| 5 | 🟠 HIGH | Зависимости | Next.js 15.5.25, sharp, postcss — известные уязвимости; Spring Boot 3.3.6 вне поддержки |
| 6 | 🟡 MEDIUM | Безопасность / баг | Гонка в лимите сессий: «потерянная» refresh-сессия переживает сброс пароля и logout-all; тест падает, бэкенд-сборка красная |
| 7 | 🟡 MEDIUM | Баг / закон | Отписка из письма не работает: ссылка открывает JSON, заголовка `List-Unsubscribe` нет |
| 8 | 🟡 MEDIUM | Безопасность | Анонимная presigned-загрузка фото без лимита размера и с SVG |
| 9 | 🟡 MEDIUM | Безопасность | Медиа в админке: тип файла берётся со слов клиента, SVG/`application/*` идут в публичный бакет |
| 10 | 🟡 MEDIUM | Безопасность | Mailgun-вебхук: подпись можно переиграть с поддельным телом |
| 11 | 🟡 MEDIUM | Безопасность | Нет CSP ни на витрине, ни в админке; у админки вообще нет заголовков |
| 12 | 🟡 MEDIUM | Доступ | Смена роли не отзывает сессии; можно снять последнего ADMIN |
| 13 | 🟡 MEDIUM | Конфиг | Адрес API во фронтах с тихим фолбэком; Flyway в prod не выключен конфигом |
| 14–21 | 🟢 LOW | разное | см. раздел LOW |

\* CRITICAL по последствиям при ошибке конфигурации. В текущем коде это не уязвимость, а открытая
дверь, которую держит закрытой только правильная переменная окружения.

---

## 🔴 1. Fail-open при незаданном профиле

**Где:** [Dockerfile](../Dockerfile), [application.yml:7](../src/main/resources/application.yml),
[SecurityConfig.java](../src/main/java/com/tzevabest/iam/web/SecurityConfig.java).

`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`, а в Dockerfile профиль не задан, и в
`main.yml` тоже. Если при деплое забыть переменную, контейнер поднимется в `local`, и тогда:

- работает `devSecurityFilterChain` с `anyRequest().permitAll()`;
- `JWT_SECRET` берёт дефолт `dev-only-insecure-jwt-secret-change-me-please-32b` — он лежит в
  репозитории. Значит, кто угодно подписывает access-токен с `role=ADMIN` и получает полный доступ к
  админ-API, включая экспорт и анонимизацию персональных данных;
- то же со спам-, privacy- и notification-секретами;
- `ProdSecretsGuard` не срабатывает: он `@Profile("prod")`;
- наружу открыты Swagger и `/actuator/metrics`, `/actuator/prometheus`.

**Фикс (сделать оба):**
1. `ENV SPRING_PROFILES_ACTIVE=prod` в runtime-стадии Dockerfile; local/test задавать явно.
2. Перевернуть защиту: dev-цепочку и dev-дефолты включать только при явном `local`/`test`
   (`@Profile({"local","test"})`), а без профиля или с неизвестным профилем отказываться
   стартовать. Как вариант — guard, который роняет старт, если `JWT_SECRET` равен dev-дефолту при
   любом профиле, кроме `local`/`test`.

## 🟠 2. Сброс пароля не работает

**Где:** [PasswordService.java:52](../src/main/java/com/tzevabest/iam/service/PasswordService.java).

- Ссылка строится как `app.web.base-url + "/reset-password?token=…"`. Но `app.web.base-url` — это
  адрес **API**: тот же base-url используют ссылки подтверждения email и отписки, и они ведут на
  `/api/v1/...`. Страницы `/reset-password` нет ни в API, ни в админке
  ([router.tsx](../admin/src/router.tsx)), ни на витрине. Сотрудник, забывший пароль, войти не сможет.
- `APP_BASE_URL` не обязателен в `application-prod.yml` и отсутствует в `.env.example`, поэтому в
  prod все ссылки из писем ведут на `http://localhost:8080`.
- Тексты писем о сбросе захардкожены на английском (`"Reset your password: "`), без i18n. При этом
  письмо о краже токена локализовано.

**Фикс:** экран `/reset-password` в админке (POST `/api/v1/auth/password/reset`); отдельный
параметр `app.web.admin-url` для ссылок на фронт; `APP_BASE_URL` (и новый параметр) — обязательные в
prod; тексты писем — через `MessageSource`.

## 🟠 3. Нельзя завести и заблокировать сотрудника

**Где:** [AdminUserController.java](../src/main/java/com/tzevabest/iam/web/AdminUserController.java),
[docs/account-lifecycle.md](account-lifecycle.md).

Документ говорит, что «пользователей заводит администратор — через эндпоинт смены роли». Но этот
эндпоинт меняет роль **существующего** пользователя, а создать пользователя нельзя:
публичная регистрация закрыта (`AUTH_PUBLIC_REGISTRATION=false`), а `new User()` в коде есть только в
`AdminBootstrap` (один ADMIN на пустой БД) и в `register`. Экрана управления пользователями в админке
тоже нет: смену роли можно сделать только curl'ом.

Статус `BLOCKED` в модели есть, но его не выставляет ни один код. Уволенного сотрудника нельзя
отключить. Остаётся только понизить его до `CUSTOMER` через API, а сессии при этом не отзываются
(см. п. 12).

Обходной путь «временно открыть регистрацию» в prod опасен: в это окно зарегистрироваться может кто
угодно.

**Фикс:** `POST /admin/users` (ADMIN: email, имя, роль; пароль задаётся по ссылке-приглашению через
тот же one-time-token, что и при сбросе), `POST /admin/users/{id}/block|unblock` с
`revokeAllForUser`, минимальный экран «Сотрудники» в админке.

## 🟠 4. Деплой-конвейер из заглушек

**Где:** [.github/workflows/main.yml](../.github/workflows/main.yml).

- `deploy-staging` и `deploy-prod` — только `echo`: миграции Flyway не запускаются, образ никуда не
  выкатывается, smoke-тесты не идут. `docs/deployment.md` описывает конвейер так, будто он работает.
- Скан образа Trivy стоит с `exit-code: '0'`: HIGH/CRITICAL в образе не блокируют публикацию.
- У витрины нет Dockerfile: в `next.config.ts` стоит `output: "standalone"` «для Docker-образа
  (F-11.2)», но самого образа нет. У админки нет ни образа, ни nginx/статического конфига. Как эти
  два фронта попадут в прод, не описано.

**Фикс:** реальные шаги (`flyway migrate` с секретами окружения, деплой в ECS/k8s/…, `curl -f` по
`/actuator/health/readiness`), блокирующий Trivy-скан, Dockerfile для `web/` и конфиг раздачи
`admin/dist` (с заголовками из п. 11).

## 🟠 5. Уязвимые и устаревшие зависимости

`pnpm audit --prod` по `web/`: **8 уязвимостей (4 high, 4 moderate)**. По `admin/` — 0.

| Пакет | Версия | Проблема | Чинится в |
|---|---|---|---|
| `next` | 15.5.25 | Cache poisoning SSG/ISR в self-hosted, подмена контента между пользователями (GHSA-4jqv-mc3x-m676, GHSA-mcj8-r9mp-w47p) | ≥ 15.5.27 |
| `sharp` (через next/image) | < 0.35.5 | HIGH: уязвимость в librsvg (GHSA-wq5f-xc86-pv6w) | ≥ 0.35.5 |
| `postcss` | ≤ 8.5.22 | HIGH: чтение произвольных `.map`-файлов через `sourceMappingURL`, XSS в stringify | ≥ 8.5.23 |
| `source-map-js` | < 1.2.2 | HIGH: DoS | ≥ 1.2.2 |

Postcss и source-map-js работают только при сборке, поэтому реальный риск в рантайме несут next и
sharp. Но PR-проверка `pr.yml` (Trivy fs, `exit-code: 1`) упадёт на этих пакетах при первом же PR в
`main`.

**Бэкенд:** Spring Boot **3.3.6** (ноябрь 2024). Открытая поддержка ветки 3.3 закончилась в 2025
году, и патчи Spring Security, Spring Framework и Tomcat, вышедшие после этого, сюда не приходят.
Обновиться до актуальной поддерживаемой ветки (3.5.x или новее) и прогнать тесты. Локально бэкенд
сканером CVE не проверялся (trivy/OWASP DC не установлены); в CI это делает Trivy в `pr.yml`.

**Фикс:** `pnpm up next@^15.5.27` (подтянет sharp/postcss), при необходимости `pnpm.overrides`;
поднять Spring Boot; добавить Dependabot/Renovate.

## 🟡 6. Гонка в лимите сессий: сессия переживает отзыв

**Где:** [RefreshTokenStore.java:59](../src/main/java/com/tzevabest/iam/service/RefreshTokenStore.java)
(Lua-скрипт `ISSUE`). Проявилось при прогоне: `RefreshTokenStoreTest.parallel_logins_keep_exactly_max_sessions`
**упал** (ожидалось 5 сессий, получилось 6), поэтому `./gradlew check` сейчас красный.

Score в индексе — `now` в миллисекундах. Если несколько логинов попадают в одну миллисекунду, у них
одинаковый score, и `ZRANGE` упорядочивает их лексикографически по tokenId. Тогда скрипт может
выбрать для вытеснения **только что добавленную** сессию: удалить её из индекса, а следом
безусловно записать её hash (`HSET key …`). В итоге остаётся валидная refresh-сессия, которой нет в
индексе. `REVOKE_ALL` обходит только индекс, поэтому такая сессия **переживает** сброс пароля,
«выйти на всех устройствах» и отзыв при обнаружении кражи токена — до 30 дней. Вдобавок лимит
сессий превышается.

Злоумышленник, знающий пароль, может спровоцировать это сам: пачка параллельных логинов (лимит —
10 за 5 минут на IP). Так он получит сессию, которую не убьёт смена пароля жертвой.

**Фикс:** при вытеснении исключать текущий `tokenId` (брать кандидатов `ZRANGE` без него или
вытеснять до `ZADD` до `maxSessions-1`); score с tie-breaker'ом (например, `now` + счётчик
`INCR`). Тест оставить, он корректно ловит дефект. Флаки-тестом его не считать.

## 🟡 7. Отписка из рассылки не работает

**Где:** [UnsubscribeController.java](../src/main/java/com/tzevabest/notification/web/UnsubscribeController.java),
[UnsubscribeService.java:56](../src/main/java/com/tzevabest/notification/service/UnsubscribeService.java).

- Ссылка из письма ведёт на `GET /api/v1/notifications/unsubscribe?token=…`. Пользователь видит в
  браузере сырой JSON `{"action":"unsubscribe","confirm_required":true}`, а кнопки подтверждения нет.
  Отписаться руками нельзя.
- В javadoc описана одношаговая отписка по RFC 8058, но заголовки `List-Unsubscribe` и
  `List-Unsubscribe-Post` в письма не добавляются: в `notification/` нет ни одного `setHeader`.
- Для рекламных рассылок в Израиле (закон о связи, поправка 40, «закон о спаме») простая отписка
  обязательна. Gmail и Yahoo с 2024 года требуют one-click unsubscribe от массовых отправителей.

**Фикс:** страница подтверждения отписки на витрине, которая делает POST; ссылка в письме — на
витрину. Заголовки `List-Unsubscribe: <https://api…/unsubscribe?token=…>` и
`List-Unsubscribe-Post: List-Unsubscribe=One-Click` в письмах.

## 🟡 8. Анонимная загрузка фото: без лимита размера, с SVG

**Где:** [MediaPresigner.java:32](../src/main/java/com/tzevabest/media/service/MediaPresigner.java),
[ServiceRequestService.java:73](../src/main/java/com/tzevabest/service/service/ServiceRequestService.java).

Анонимный `POST /service-requests/photos/presign` (20 запросов в час на IP, до 10 файлов в каждом)
выдаёт presigned **PUT** без ограничения `Content-Length`. Каждый PUT в S3 может весить до 5 ГБ, так
что с одного IP за час можно залить до ~1 ТБ за счёт магазина. Уборка «осиротевших» загрузок
(`upload-intent-retention`) удалит их только через сутки. Проверка типа — `startsWith("image/")`, то
есть проходит и `image/svg+xml`. Такой файл потом открывается сотруднику по presigned GET.

**Фикс:** presigned **POST** с policy `content-length-range` (например, до 10 МБ) или проверка
`HeadObject` по размеру при создании заявки с удалением лишнего; whitelist
`image/jpeg|png|webp|heic`.

## 🟡 9. Медиа в админке: тип файла со слов клиента, публичный бакет

**Где:** [ProductMediaService.java:97-116](../src/main/java/com/tzevabest/catalog/service/ProductMediaService.java),
[PortfolioAdminService.java:186-190](../src/main/java/com/tzevabest/service/service/PortfolioAdminService.java).

Тип проверяется по `MultipartFile.getContentType()`, а его присылает клиент. Проверки содержимого
(magic bytes) нет. Проходят `image/svg+xml`, а для DOC — любой `application/*` (например,
`application/xhtml+xml`). Файлы ложатся в **публичный** бакет и раздаются с тем же типом, поэтому
SVG или XHTML с `<script>` — это stored XSS на домене медиа/CDN. Если CDN живёт на поддомене
магазина, это бьёт и по кукам. Загружать может только MANAGER/ADMIN, то есть риск в основном от
инсайдера или угнанного аккаунта.

**Фикс:** whitelist конкретных типов и сверка magic bytes; для документов
`Content-Disposition: attachment`; SVG не принимать или санитизировать.

## 🟡 10. Mailgun-вебхук: подпись переигрывается

**Где:** [MailgunWebhookVerifier.java](../src/main/java/com/tzevabest/notification/service/webhook/MailgunWebhookVerifier.java),
[NotificationWebhookService.java:66](../src/main/java/com/tzevabest/notification/service/NotificationWebhookService.java).

У Mailgun HMAC покрывает только `timestamp + token`, а не тело события. Защита от повторов здесь
дедуплицирует по хешу **тела**. Возраст `timestamp` не проверяется, использованный `token` не
кэшируется. Достаточно одной подсмотренной тройки `timestamp/token/signature` (из логов, прокси,
отладки), чтобы бесконечно слать поддельные тела с любым `message_id` и `status=failed` и портить
журнал доставки.

**Фикс:** отвергать `timestamp` старше ~5 минут и хранить использованные `token` в Redis (так и
рекомендует Mailgun).

## 🟡 11. Нет CSP; у админки нет никаких заголовков

У витрины есть базовые заголовки, но CSP отложен «на прокси/CDN», а конфига прокси в репозитории нет.
У админки (SPA, где сотрудник держит access-токен в памяти) нет ни CSP, ни `X-Frame-Options`, ни HSTS.
Конфига раздачи нет вовсе (см. п. 4). Именно CSP — главный барьер против XSS, ради которого
refresh-токен перенесли в httpOnly-куку.

**Фикс:** задать CSP (`default-src 'self'; connect-src <api>; img-src 'self' <cdn> data:;
frame-ancestors 'none'; …`) для обоих фронтов в конфиге раздачи, который появится в п. 4.

## 🟡 12. Смена роли: сессии живы, можно остаться без админа

**Где:** [AdminUserService.java:59](../src/main/java/com/tzevabest/iam/service/AdminUserService.java).

- После `changeUserRole` refresh-сессии не отзываются. Уже выданный access-токен до 15 минут
  работает со **старой** ролью: роль берётся из JWT и с БД не сверяется.
- ADMIN может понизить сам себя или последнего ADMIN, и тогда управлять ролями будет некому.
  Восстановить можно только SQL'ем: `AdminBootstrap` срабатывает лишь на пустой таблице.

**Фикс:** `refreshStore.revokeAllForUser(userId)` при смене роли; запрет понижать последнего
активного ADMIN.

## 🟡 13. Конфиг с тихими фолбэками

- Витрина: `NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"` вшивается **при сборке**. Если
  забыть переменную в CI, прод-витрина будет ходить на localhost посетителя. Админка: `?? ""` (тот же
  origin), что тоже работает только при определённой схеме деплоя. Лучше ронять сборку, если
  переменная не задана.
- Flyway: в `deployment.md` написано «в prod задать `SPRING_FLYWAY_ENABLED=false`», но
  `application-prod.yml` этого не делает. При нескольких инстансах возможна гонка миграций при
  старте. Прописать `spring.flyway.enabled: ${SPRING_FLYWAY_ENABLED:false}` в prod-профиле.
- `REDIS_PASSWORD` в prod необязателен (дефолт пустой). Если Redis не в изолированной сети, в нём
  лежат refresh-сессии и OTP.

## 🟢 LOW

14. **Подтверждение email по GET меняет состояние**
    ([EmailVerificationController.java:33](../src/main/java/com/tzevabest/iam/web/EmailVerificationController.java)).
    Сканеры ссылок (Outlook Safe Links, антивирусы) «съедают» одноразовый токен, а пользователь
    видит пустую страницу 204. Лучше страница на фронте с POST.
15. **Сброс пароля:** новый `forgot` не гасит прежние токены (параллельно живут несколько ссылок);
    `reset` не проверяет статус пользователя; у `/forgot` лимит только по логину, без лимита по IP.
16. **Смена пароля** (`/auth/password/change`) без rate limit, хотя аутентифицированный злоумышленник
    может подбирать текущий пароль по украденному access-токену. Добавить `@RateLimit(key="user")`.
17. **`AdminReportController`/`ReportService`** остались в API, хотя раздел отчётов решено не делать
    (2026-10-13). Это лишняя поверхность атаки: удалить или закрыть.
18. **GitHub Actions** закреплены тегами (`@v4`, `trivy-action@0.24.0`), а не SHA коммитов. Для
    сторонних actions (aquasecurity, docker/*) это риск цепочки поставок. `trivy-action@0.24.0`
    сильно устарел.
19. **`management.info.env.enabled: true`** — всё из `info.*` уходит в публичный `/actuator/info`.
    Сейчас безвредно, но легко случайно выставить что-то лишнее.
20. **`next lint` устарел** (удалят в Next 16) — мигрировать на ESLint CLI. В админке 3 warning'а
    `react-refresh/only-export-components`.
21. **Dev-аккаунты** (`dev-accounts.env.local`) — ротировать или удалить перед передачей, как уже
    отмечено в чек-листе прошлого аудита.

---

## Что проверено и в порядке

- **SQL-инъекции:** весь динамический SQL параметризован. `ORDER BY` собирается из whitelist
  `SORTABLE`, колонка владельца медиа — из enum.
- **JWT:** HS256, `requireIssuer`, разделение `type=access|refresh`. Refresh хранится в Redis с
  ротацией, детектом повторного использования и отзывом всех сессий. При refresh роль и статус
  перечитываются из БД.
- **Refresh-кука:** `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/api/v1/auth`; проверка `Origin`
  на refresh/logout. Access-токен в админке живёт только в памяти.
- **Логин:** одинаковое время для несуществующих пользователей (dummy bcrypt), rate limit по IP и по
  логину, BCrypt(12).
- **OTP и одноразовые токены:** 256-битные токены; в Redis хранится SHA-256, потребление атомарное
  (GETDEL). У OTP атомарный счётчик попыток (Lua) и cooldown.
- **Права:** у всех `/admin/**` двойной контроль — URL по роли сотрудника плюс `@PreAuthorize` на
  классе контроллера или сервиса. Чувствительное (аудит, приватность, смена ролей) — только ADMIN.
  Клиентские ресурсы проверяются через `@ownership`.
- **IP за прокси:** `X-Forwarded-For` учитывается только от доверенных CIDR (`TRUSTED_PROXIES`
  обязателен в prod).
- **Вебхуки:** HMAC сравнивается в константное время (`MessageDigest.isEqual`).
- **Ошибки:** `server.error.include-message: never`, ответы в problem+json без стектрейсов.
- **Фронт:** нет `eval`/`innerHTML`, а единственный `dangerouslySetInnerHTML` (JSON-LD) экранирует `<`.
- **Контейнер:** non-root, multi-stage, HEALTHCHECK.

## Прогоны

| Проверка | Результат |
|---|---|
| `web`: `tsc --noEmit` / `next lint` / `vitest` | ✅ / ✅ 0 замечаний / ✅ 20 тестов |
| `admin`: `tsc -b` / `eslint` / `vitest` | ✅ / ⚠️ 3 warning / ✅ 21 тест |
| `pnpm audit --prod` | ❌ `web`: 4 high + 4 moderate; ✅ `admin`: 0 |
| Бэкенд `./gradlew check` | ❌ 502 теста, **1 упал**: `RefreshTokenStoreTest.parallel_logins_keep_exactly_max_sessions` — реальный дефект, п. 6 |
| e2e, нагрузка, скан образа | не запускались (нужен stage и реальное окружение) |

## Порядок исправления

1. **До выката:** п. 1 (профиль + fail-closed), п. 5 (next/sharp, Spring Boot), п. 2 (сброс
   пароля + `APP_BASE_URL`), п. 3 (создание и блокировка сотрудников), п. 4 (реальный деплой и
   образы фронтов).
2. **В первый релиз или сразу после:** п. 6–13 (п. 6 — сразу: из-за него красный CI).
3. **Бэклог:** LOW.
