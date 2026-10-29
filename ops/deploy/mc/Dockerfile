# MinIO client (mc) из GitHub-релиза (dl.min.io заархивирован → 410; у mc ассеты ещё публикуются).
# arm64 под Oracle Ampere A1. Нужен только для создания бакетов в SeaweedFS (mc S3-совместим).
FROM alpine:3.20
ARG MC_URL=https://github.com/minio/mc/releases/download/RELEASE.2025-08-13T08-35-41Z/mc.linux-arm64.RELEASE.2025-08-13T08-35-41Z
RUN apk add --no-cache ca-certificates bash \
 && wget -qO /usr/bin/mc "$MC_URL" \
 && chmod +x /usr/bin/mc
ENTRYPOINT ["mc"]
