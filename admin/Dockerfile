# ── Сборка админки (Vite → статика) ─────────────────────────────────────────────
# Контекст сборки — папка admin/. VITE_API_BASE_URL инлайнится на сборке (build-arg).
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build

# ── Раздача статики (nginx с заголовками безопасности и CSP, аудит п.11) ─────────
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/ >/dev/null 2>&1 || exit 1
