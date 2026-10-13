import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// Прокси на бэкенд в dev: refresh-кука httpOnly ставится на домене API, поэтому фронт и API
// удобно держать за одним origin'ом localhost:5173, чтобы кука долетала (см. B-01).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        // API_PROXY_TARGET — куда проксировать без смены базы клиента (e2e-стек на :18080, см. e2e/).
        target: process.env.API_PROXY_TARGET ?? process.env.VITE_API_BASE_URL ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
