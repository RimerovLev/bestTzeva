#!/usr/bin/env bash
# Прогон e2e в официальном Linux-образе Playwright — так же, как в CI. Скриншотные эталоны
# снимаются только так: шрифты и сглаживание на macOS другие, эталон с Mac в CI не совпадёт.
# Стек (scripts/stack.sh up) должен быть поднят на хосте; база сбрасывается перед прогоном.
#   bash scripts/in-docker.sh                       — все тесты
#   bash scripts/in-docker.sh --update-snapshots    — переснять эталоны
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
VERSION=$(node -p "require('$ROOT/e2e/node_modules/@playwright/test/package.json').version")
IMAGE="mcr.microsoft.com/playwright:v${VERSION}-noble"

[ "${E2E_SKIP_RESET:-}" = "1" ] || bash "$ROOT/e2e/scripts/stack.sh" reset

docker run --rm --ipc=host \
  --add-host=host.docker.internal:host-gateway \
  -e CI="${CI:-}" -e E2E_A11Y_DUMP="${E2E_A11Y_DUMP:+/work/e2e/.run/a11y.jsonl}" \
  -v "$ROOT:/work" -w /work/e2e \
  "$IMAGE" \
  bash -c 'node scripts/forward.mjs & sleep 1; node_modules/.bin/playwright test "$@"' _ "$@"
