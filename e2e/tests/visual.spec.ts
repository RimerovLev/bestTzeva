import { expect, test, type Page } from "@playwright/test";
import webRu from "../../web/src/dictionaries/ru.json" with { type: "json" };
import webHe from "../../web/src/dictionaries/he.json" with { type: "json" };
import adminRu from "../../admin/src/dictionaries/ru.json" with { type: "json" };
import adminHe from "../../admin/src/dictionaries/he.json" with { type: "json" };
import { ADMIN, ADMIN_EMAIL, ADMIN_PASSWORD, WEB, readSeed } from "../env";

/**
 * Скриншоты ключевых страниц в обеих локалях (F-11.1): RTL ломается не в тексте, а в иконках со
 * стрелками, отступах и таблицах — это видно только глазами или пикселями. Анимации выключены
 * (prefers-reduced-motion), WebGL-холсты замаскированы. Эталоны — только из Linux-образа
 * Playwright (scripts/in-docker.sh), иначе шрифты рендерятся по-разному.
 */
const WEB_DICT = { he: webHe, ru: webRu } as const;
const ADMIN_DICT = { he: adminHe, ru: adminRu } as const;

/** Страница дорисована: шрифты загружены, скелетонов нет, видимые картинки загружены. */
async function settle(page: Page) {
  await page.waitForLoadState("load");
  // WebGL-холсты (жидкость в герое, примерочная) рисуются по-разному на разных GPU — прячем, а не
  // маскируем: холст героя лежит поверх всего первого экрана, маска закрыла бы вёрстку целиком
  await page.addStyleTag({ content: "canvas.hero-fluid, canvas.vz-canvas, canvas.vz-orig { visibility: hidden !important; }" });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => !document.querySelector(".animate-pulse, .sk-line"));
  // ленивые картинки ниже экрана не грузятся, пока до них не докрутили — ждём только видимые
  await page.waitForFunction(() =>
    [...document.images].every((img) => {
      const r = img.getBoundingClientRect();
      return img.complete || r.bottom < 0 || r.top > innerHeight;
    }),
  );
}

/** Маска: бейдж наличия — его меняют сценарии flows, идущие в той же базе. */
const masks = (page: Page) => [page.locator(".avail")];
const shot = (page: Page, name: string, opts: { fullPage?: boolean } = {}) =>
  expect(page).toHaveScreenshot(name, { fullPage: opts.fullPage ?? false, mask: masks(page) });

for (const locale of ["he", "ru"] as const) {
  const d = WEB_DICT[locale];

  test.describe(`витрина ${locale}`, () => {
    test("главная: первый экран и подвал", async ({ page }) => {
      await page.goto(`${WEB}/${locale}`);
      await settle(page);
      await shot(page, `${locale}-home.png`);
      await page.locator("footer").scrollIntoViewIfNeeded();
      await settle(page);
      await expect(page.locator("footer")).toHaveScreenshot(`${locale}-footer.png`, { mask: masks(page) });
    });

    test("каталог с фильтрами", async ({ page }) => {
      await page.goto(`${WEB}/${locale}/products?q=E2E`);
      await settle(page);
      await shot(page, `${locale}-catalog.png`);
    });

    test("карточка товара", async ({ page }) => {
      await page.goto(`${WEB}/${locale}/products/${readSeed().productSlug}`);
      await settle(page);
      await shot(page, `${locale}-product.png`);
    });

    test("заявка с позицией", async ({ page }) => {
      await page.goto(`${WEB}/${locale}/products/${readSeed().productSlug}`);
      await page.getByRole("button", { name: /10/ }).first().click();
      await page.getByRole("button", { name: d.product.addToRequest }).click();
      await page.goto(`${WEB}/${locale}/request`);
      await expect(page.getByRole("button", { name: d.request.submit })).toBeVisible();
      await settle(page);
      await shot(page, `${locale}-request.png`, { fullPage: true });
    });

    test("форма замера, первый шаг", async ({ page }) => {
      await page.goto(`${WEB}/${locale}/services/measure`);
      await settle(page);
      await shot(page, `${locale}-measure.png`);
    });
  });

  test.describe(`админка ${locale}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((l) => localStorage.setItem("tb.admin.locale", l), locale);
    });

    test("вход и склад", async ({ page }) => {
      const t = ADMIN_DICT[locale];
      await page.goto(`${ADMIN}/`);
      await settle(page);
      await shot(page, `${locale}-admin-login.png`);

      await page.getByLabel(t.login.loginLabel).fill(ADMIN_EMAIL);
      await page.getByLabel(t.login.passwordLabel).fill(ADMIN_PASSWORD);
      await page.getByRole("button", { name: t.login.submit }).click();
      await page.getByRole("navigation").getByRole("link", { name: t.nav.warehouse }).click();
      // вариант 3 л сценарии не трогают — строка стабильна при любом порядке проектов
      await page.getByLabel(t.inventory.search).fill(readSeed().otherVariantSku);
      await expect(page.getByRole("row").filter({ hasText: readSeed().otherVariantSku })).toBeVisible();
      await expect(page.getByRole("row")).toHaveCount(2);
      await expect(page.locator(".animate-pulse")).toHaveCount(0);
      await settle(page);
      await shot(page, `${locale}-admin-warehouse.png`);
    });
  });
}
