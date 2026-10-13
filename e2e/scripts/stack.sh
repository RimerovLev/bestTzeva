#!/usr/bin/env bash
# Изолированный стек для e2e (F-11.1): реальный бэкенд, витрина и админка поверх docker-compose,
# но со СВОЕЙ базой и портами — рабочая база и dev-серверы (8080/3000/5173) не трогаются.
#
#   бэкенд  :18080  база tzevabest_e2e (пересоздаётся), Redis db 1 (очищается)
#   витрина :3100   next build в .next-e2e + next start
#   админка :5174   vite build + vite preview (прокси /api → :18080)
#
# Использование: stack.sh up | reset | down | logs
# E2E_SKIP_BUILD=1 — не пересобирать (повторный прогон без изменений кода).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RUN="$ROOT/e2e/.run"
BACKEND_PORT=${E2E_BACKEND_PORT:-18080}
WEB_PORT=${E2E_WEB_PORT:-3100}
ADMIN_PORT=${E2E_ADMIN_PORT:-5174}
DB=${E2E_DB_NAME:-tzevabest_e2e}
GRADLE_FLAGS=${E2E_GRADLE_FLAGS:-}
# Учётка админа создаётся AdminBootstrap в пустой одноразовой базе; значения только для e2e.
ADMIN_EMAIL=${E2E_ADMIN_EMAIL:-e2e-admin@tzevabest.local}
ADMIN_PASSWORD=${E2E_ADMIN_PASSWORD:-e2e-only-Admin-pass-1}

# Реквизиты Postgres — те же, что у docker-compose (из .env, если он есть).
env_val() { [ -f "$ROOT/.env" ] && grep -E "^$1=" "$ROOT/.env" | tail -1 | cut -d= -f2- | sed 's/[[:space:]]*#.*//' || true; }
DB_USER=${DB_USERNAME:-$(env_val DB_USERNAME)}; DB_USER=${DB_USER:-tzevabest}
DB_PASS=${DB_PASSWORD:-$(env_val DB_PASSWORD)}; DB_PASS=${DB_PASS:-tzevabest_secret}

compose() { docker compose -f "$ROOT/docker-compose.yml" "$@"; }
psql_e2e() { compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$DB_USER" "$@"; }

wait_http() { # url, имя, секунд
  local url=$1 name=$2 limit=${3:-120} i=0
  until curl -fs -o /dev/null "$url"; do
    i=$((i + 1))
    if [ "$i" -ge "$limit" ]; then echo "✗ $name не поднялся за ${limit}s ($url), см. $RUN/$name.log" >&2; tail -40 "$RUN/$name.log" >&2 || true; exit 1; fi
    sleep 1
  done
  echo "✓ $name: $url"
}

start() { # имя, команда… (в фоне, лог и pid в .run)
  local name=$1; shift
  ( "$@" >"$RUN/$name.log" 2>&1 & echo $! >"$RUN/$name.pid" )
}

stop_all() {
  for f in "$RUN"/*.pid; do
    [ -e "$f" ] || continue
    local pid; pid=$(cat "$f")
    # убиваем только свои процессы (и их детей) по сохранённым pid
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
    rm -f "$f"
  done
}

fresh_db() {
  echo "→ чистая база $DB и Redis db 1"
  psql_e2e -d postgres -c "drop database if exists $DB with (force)" -c "create database $DB" >/dev/null
  compose exec -T redis redis-cli -n 1 flushdb >/dev/null
}

start_backend() {
  local jar
  jar=$(ls "$ROOT"/build/libs/*.jar | grep -v -- '-plain' | head -1)
  echo "→ бэкенд $jar"
  start backend env \
    SERVER_PORT="$BACKEND_PORT" SPRING_PROFILES_ACTIVE=local \
    DB_NAME="$DB" DB_USERNAME="$DB_USER" DB_PASSWORD="$DB_PASS" \
    SPRING_DATA_REDIS_DATABASE=1 \
    CORS_ALLOWED_ORIGINS="http://localhost:$WEB_PORT,http://localhost:$ADMIN_PORT" \
    SPAM_MIN_FILL_SECONDS=0 \
    BOOTSTRAP_ADMIN_EMAIL="$ADMIN_EMAIL" BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    LOGGING_LEVEL_COM_TZEVABEST=INFO LOGGING_LEVEL_ORG_HIBERNATE_SQL=WARN SPRING_JPA_SHOW_SQL=false \
    java -jar "$jar"
  wait_http "http://localhost:$BACKEND_PORT/actuator/health/readiness" backend 180

  # Бренды правятся только в БД (админского API нет) — один тестовый бренд.
  psql_e2e -d "$DB" -qAt -c "insert into brands (slug, name) values ('e2e', 'E2E Paints') returning id" >"$RUN/brand_id"
}

start_frontends() {
  echo "→ витрина и админка"
  start web env NEXT_DIST_DIR=.next-e2e NEXT_TELEMETRY_DISABLED=1 PORT="$WEB_PORT" HOSTNAME=0.0.0.0 \
    bash -c "cd '$ROOT/web' && exec node_modules/.bin/next start -p $WEB_PORT -H 0.0.0.0"
  start admin env API_PROXY_TARGET="http://localhost:$BACKEND_PORT" \
    bash -c "cd '$ROOT/admin' && exec node_modules/.bin/vite preview --port $ADMIN_PORT --strictPort --host 0.0.0.0"
  wait_http "http://localhost:$WEB_PORT/he" web 90
  wait_http "http://localhost:$ADMIN_PORT/" admin 60
}

stop_one() {
  local f="$RUN/$1.pid"
  [ -e "$f" ] || return 0
  local pid; pid=$(cat "$f")
  pkill -TERM -P "$pid" 2>/dev/null || true
  kill "$pid" 2>/dev/null || true
  rm -f "$f"
  # дождаться, пока порт освободится
  for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
}

# Перед каждым прогоном: чистая база и Redis, перезапуск бэкенда и витрины (её кэш страниц —
# в памяти процесса). Без пересборки, ~20–30 с. Сценарии рассчитывают на чистую базу.
reset() {
  mkdir -p "$RUN"
  stop_one web; stop_one backend
  fresh_db
  start_backend
  stop_one admin
  start_frontends
  echo "Стек e2e сброшен."
}

up() {
  mkdir -p "$RUN"
  stop_all

  for p in "$BACKEND_PORT" "$WEB_PORT" "$ADMIN_PORT"; do
    if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then echo "✗ порт $p занят чужим процессом" >&2; exit 1; fi
  done

  echo "→ инфраструктура docker-compose"
  compose up -d --wait postgres redis minio mailpit
  compose up minio-init >/dev/null
  fresh_db

  if [ "${E2E_SKIP_BUILD:-}" != "1" ]; then
    echo "→ сборка бэкенда"
    (cd "$ROOT" && ./gradlew bootJar -x test -q $GRADLE_FLAGS)
  fi
  # бэкенд — до сборки витрины: `next build` пререндерит страницы, запрашивая API
  start_backend

  if [ "${E2E_SKIP_BUILD:-}" != "1" ]; then
    echo "→ сборка витрины"
    (cd "$ROOT/web" && NEXT_DIST_DIR=.next-e2e NEXT_PUBLIC_API_BASE_URL="http://localhost:$BACKEND_PORT" \
      NEXT_PUBLIC_SITE_URL="http://localhost:$WEB_PORT" NEXT_TELEMETRY_DISABLED=1 corepack pnpm -s build >"$RUN/web-build.log" 2>&1) \
      || { tail -40 "$RUN/web-build.log" >&2; exit 1; }
    echo "→ сборка админки"
    (cd "$ROOT/admin" && env -u VITE_API_BASE_URL corepack pnpm -s build >"$RUN/admin-build.log" 2>&1) \
      || { tail -40 "$RUN/admin-build.log" >&2; exit 1; }
  fi

  start_frontends
  echo "Стек e2e готов."
}

case "${1:-up}" in
  up) up ;;
  reset) reset ;;
  down) stop_all; echo "Стек e2e остановлен." ;;
  logs) tail -n 60 "$RUN"/*.log ;;
  *) echo "usage: $0 up|reset|down|logs" >&2; exit 2 ;;
esac
