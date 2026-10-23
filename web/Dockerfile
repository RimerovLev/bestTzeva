# ── Сборка витрины (Next.js standalone) ────────────────────────────────────────
# Контекст сборки — папка web/. Переменные NEXT_PUBLIC_* инлайнятся на этом шаге,
# поэтому адреса API/сайта передаются как build-args в CI (иначе уедет localhost).
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
# pnpm-workspace.yaml несёт разрешение сборки esbuild и overrides безопасности (postcss/source-map-js).
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
RUN pnpm build

# ── Рантайм ─────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
ENV NODE_ENV=production
ENV PORT=3000
# Next standalone кладёт самодостаточный сервер (server.js) + минимальные node_modules.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER app
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://localhost:3000/ >/dev/null 2>&1 || exit 1
CMD ["node", "server.js"]
