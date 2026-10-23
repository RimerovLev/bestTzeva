import type { NextConfig } from "next";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const projectRoot = dirname(fileURLToPath(import.meta.url));

/** Origin из URL или пустая строка (для безопасной подстановки в CSP). */
function origin(url: string | undefined): string {
  if (!url) return "";
  try {
    return new URL(url).origin;
  } catch {
    return "";
  }
}

/**
 * Базовый CSP витрины (аудит п.11). Источники зависят от деплоя (API, медиа/CDN, Plausible), поэтому
 * собираются из env на сборке. Пускаем в Report-Only: Next SSR требует inline-скрипты гидрации (без
 * nonce-инфраструктуры их не убрать), а набор origin изображений/медиа зависит от окружения —
 * сначала собираем нарушения из отчётов, затем переключаем на enforcing (заголовок без -Report-Only).
 */
function contentSecurityPolicy(): string {
  const api = origin(process.env.NEXT_PUBLIC_API_BASE_URL) || "http://localhost:8080";
  const plausible = origin(process.env.NEXT_PUBLIC_PLAUSIBLE_SRC) || "https://plausible.io";
  const media = origin(process.env.NEXT_PUBLIC_MEDIA_BASE_URL);
  return [
    "default-src 'self'",
    `script-src 'self' 'unsafe-inline' ${plausible}`.trim(),
    "style-src 'self' 'unsafe-inline'",
    `img-src 'self' data: blob: ${api} ${media}`.replace(/\s+/g, " ").trim(),
    "font-src 'self'",
    `connect-src 'self' ${api} ${plausible}`.replace(/\s+/g, " ").trim(),
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; ");
}

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // standalone-сборка нужна для Docker-образа витрины (см. F-11.2).
  output: "standalone",
  // Корень трассировки — папка витрины: рядом с монорепо бэкенда есть чужие lock-файлы,
  // без этого Next выбирает не тот корень и кладёт в standalone лишнее.
  outputFileTracingRoot: projectRoot,
  poweredByHeader: false,
  // Отдельная папка сборки для e2e-стека (NEXT_DIST_DIR=.next-e2e): `next build` не должен затирать
  // .next, из которого в это же время работает `next dev`.
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // Метаданные (title, description, OG) всегда в <head>, а не потоком в <body>: Next по умолчанию
  // отдаёт их в head только известным ботам, а превью мессенджеров и аудиторы SEO не из его списка
  // видели страницу без описания. Цена — head ждёт данные товара, они и так нужны для страницы.
  htmlLimitedBots: /.*/,
  // Заголовки безопасности для всех ответов витрины (аудит п.11). CSP — в Report-Only (см. выше).
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          {
            key: "Strict-Transport-Security",
            value: "max-age=31536000; includeSubDomains",
          },
          { key: "Content-Security-Policy-Report-Only", value: contentSecurityPolicy() },
        ],
      },
    ];
  },
};

export default nextConfig;
