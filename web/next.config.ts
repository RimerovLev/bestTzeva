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
};

export default nextConfig;
