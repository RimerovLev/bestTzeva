// Lighthouse CI (F-11.1): пороги на мобильном профиле по умолчанию — производительность ≥ 85,
// доступность ≥ 95, SEO = 100. Страницы — индексируемые страницы витрины e2e-стека (формы заявки и
// замера закрыты noindex намеренно, SEO-аудит для них не имеет смысла). Слаг товара — из сида.
const { readFileSync } = require("node:fs");
const { join } = require("node:path");

const WEB = process.env.E2E_WEB_URL || "http://localhost:3100";
let slug = "e2e-velvet";
try {
  slug = JSON.parse(readFileSync(join(__dirname, ".run/seed.json"), "utf8")).productSlug;
} catch {
  // сид ещё не запускался — используем слаг по умолчанию
}

module.exports = {
  ci: {
    collect: {
      url: [`${WEB}/he`, `${WEB}/ru`, `${WEB}/he/products`, `${WEB}/he/products/${slug}`, `${WEB}/he/services`],
      numberOfRuns: 5, // медиана из пяти — главная близко к порогу, три прогона давали разброс ±0.03
      settings: {
        // прогрев: страницы ISR — первый запрос после старта сервера медленнее остальных
        chromeFlags: "--no-sandbox --headless=new",
      },
    },
    assert: {
      assertions: {
        "categories:performance": ["error", { minScore: 0.85, aggregationMethod: "median-run" }],
        "categories:accessibility": ["error", { minScore: 0.95, aggregationMethod: "median-run" }],
        "categories:seo": ["error", { minScore: 1, aggregationMethod: "median-run" }],
      },
    },
    upload: { target: "filesystem", outputDir: ".lighthouseci" },
  },
};
