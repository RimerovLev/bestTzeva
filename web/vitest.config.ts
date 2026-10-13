import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

// Юнит-тесты логики витрины (F-11.1). Next.js собирает JSX сам, здесь — esbuild с автоматическим
// рантаймом; алиас @ как в tsconfig. E2E (Playwright) живут отдельно в /e2e и сюда не попадают.
export default defineConfig({
  esbuild: { jsx: "automatic" },
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
