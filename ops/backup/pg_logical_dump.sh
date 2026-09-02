#!/usr/bin/env bash
# T-14.6: ежедневный логический дамп критичных таблиц в отдельный бакет.
# Защита от ЛОГИЧЕСКОЙ ошибки (её снапшот унаследует). Реквизиты — из окружения.
set -euo pipefail

: "${PGHOST:?}" "${PGUSER:?}" "${PGDATABASE:?}" "${BACKUP_BUCKET:?}"
TABLES=(
  product_requests product_request_items
  service_requests service_request_media service_request_work_types
  inventory stock_reservations inventory_movements
  consents audit_log
)
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="/tmp/tzevabest-critical-${STAMP}.sql.gz"

args=()
for t in "${TABLES[@]}"; do args+=("--table=public.${t}"); done

echo "[backup] dumping ${#TABLES[@]} tables → ${OUT}"
pg_dump --no-owner --no-privileges "${args[@]}" | gzip -9 > "${OUT}"

echo "[backup] uploading to s3://${BACKUP_BUCKET}/logical/${STAMP}/"
aws s3 cp "${OUT}" "s3://${BACKUP_BUCKET}/logical/${STAMP}/critical.sql.gz"
rm -f "${OUT}"

# Метрика успешности (push-gateway/healthchecks): отсутствие пинга = алерт о пропуске бэкапа.
if [[ -n "${BACKUP_HEARTBEAT_URL:-}" ]]; then curl -fsS "${BACKUP_HEARTBEAT_URL}" >/dev/null; fi
echo "[backup] done ${STAMP}"
