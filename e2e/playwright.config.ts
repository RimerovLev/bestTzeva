import { defineConfig, devices } from "@playwright/test";

/**
 * E2E Tzeva Best (F-11.1). Гоняется против настоящего бэкенда (scripts/stack.sh поднимает
 * изолированный стек), а не против моков. Проекты:
 *  - setup  — сид данных через admin API (товар, склад, стартовый остаток);
 *  - flows  — сквозные сценарии и доступность (axe, клавиатура);
 *  - visual — скриншоты ключевых страниц в обеих локалях. Пиксели зависят от ОС и шрифтов,
 *    поэтому эталоны снимаются в Linux-образе Playwright (scripts/in-docker.sh) — и в CI так же.
 */
const visualEnabled = process.platform === "linux" || process.env.E2E_VISUAL === "1";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1, // сценарии делят одну базу: заявка с витрины резервируется в админке
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  timeout: 60_000,
  expect: {
    timeout: 10_000,
    toHaveScreenshot: { maxDiffPixelRatio: 0.01, animations: "disabled", caret: "hide", scale: "css" },
  },
  // Отсутствующий эталон в CI — ошибка, а не молча записанный новый снимок.
  updateSnapshots: process.env.CI ? "none" : "missing",
  snapshotPathTemplate: "{testDir}/__screenshots__/{testFilePath}/{arg}{ext}",
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  use: {
    ...devices["Desktop Chrome"],
    viewport: { width: 1280, height: 800 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "setup", testMatch: /seed\.setup\.ts/ },
    // flows и visual независимы: скриншоты не включают то, что меняют сценарии (остатки, заявки),
    // поэтому визуальная разница не отменяет функциональные проверки и наоборот
    { name: "flows", testMatch: /(flows|measure|a11y)\.spec\.ts/, dependencies: ["setup"] },
    ...(visualEnabled
      ? [{
          name: "visual",
          testMatch: /visual\.spec\.ts/,
          dependencies: ["setup"],
          use: { reducedMotion: "reduce" as const },
        }]
      : []),
  ],
});
