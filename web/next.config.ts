import type { NextConfig } from "next";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const projectRoot = dirname(fileURLToPath(import.meta.url));

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
};

export default nextConfig;
