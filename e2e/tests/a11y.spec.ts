import { appendFileSync } from "node:fs";
import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import webRu from "../../web/src/dictionaries/ru.json" with { type: "json" };
import adminRu from "../../admin/src/dictionaries/ru.json" with { type: "json" };
import { ADMIN, ADMIN_EMAIL, ADMIN_PASSWORD, WEB, readSeed } from "../env";

/**
 * Доступность (F-11.1): axe по WCAG 2.1 A/AA на ключевых страницах обеих локалей, плюс работа с
 * клавиатуры и видимый фокус на формах. Аудитория включает пожилых людей — нарушение валит сборку.
 */
const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

async function audit(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  const report = violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.slice(0, 5).map((n) => `${n.target.join(" ")} — ${n.failureSummary?.split("\n").slice(1, 3).join("; ")}`),
  }));
  // E2E_A11Y_DUMP=файл — собрать все нарушения разом (для разбора), а не по одному падению
  if (process.env.E2E_A11Y_DUMP) {
    for (const v of violations) for (const n of v.nodes)
      appendFileSync(process.env.E2E_A11Y_DUMP, JSON.stringify({ url: page.url(), id: v.id, target: n.target.join(" "), html: n.html.slice(0, 160), why: n.failureSummary }) + "\n");
    return;
  }
  expect(report, JSON.stringify(report, null, 2)).toEqual([]);
}

const STOREFRONT = (slug: string) => ["", "/products", `/products/${slug}`, "/request", "/services", "/services/measure", "/colors", "/portfolio"];

for (const locale of ["he", "ru"] as const) {
  test.describe(`витрина ${locale}: axe`, () => {
    for (const path of STOREFRONT("{slug}")) {
      test(`/${locale}${path.replace("{slug}", "<товар>")}`, async ({ page }) => {
        const url = `${WEB}/${locale}${path.replace("{slug}", readSeed().productSlug)}`;
        await page.emulateMedia({ reducedMotion: "reduce" });
        await page.goto(url);
        await page.waitForLoadState("networkidle");
        await audit(page);
      });
    }
  });
}

test.describe("админка: axe", () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem("tb.admin.locale", "he"));
  });

  test("вход", async ({ page }) => {
    await page.goto(`${ADMIN}/`);
    await expect(page.getByRole("button")).not.toHaveCount(0);
    await audit(page);
  });

  test("рабочие экраны после входа", async ({ page }) => {
    test.setTimeout(120_000);
    const he = (await import("../../admin/src/dictionaries/he.json", { with: { type: "json" } })).default;
    await page.goto(`${ADMIN}/`);
    await page.getByLabel(he.login.loginLabel).fill(ADMIN_EMAIL);
    await page.getByLabel(he.login.passwordLabel).fill(ADMIN_PASSWORD);
    await page.getByRole("button", { name: he.login.submit }).click();
    const nav = page.getByRole("navigation");
    await expect(nav.getByRole("link", { name: he.nav.warehouse })).toBeVisible();
    // переходы через меню (SPA, без перезагрузки), экран считается готовым, когда ушли скелетоны
    for (const key of ["dashboard", "productRequests", "serviceRequests", "warehouse", "catalog"] as const) {
      await nav.getByRole("link", { name: he.nav[key] }).click();
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await expect(page.locator(".animate-pulse")).toHaveCount(0);
      await test.step(key, () => audit(page));
    }
  });
});

test.describe("клавиатура", () => {
  test("форма заявки проходится Tab-ом, фокус виден", async ({ page }) => {
    const r = webRu.request;
    const seed = readSeed();
    // в заявке должна быть позиция, иначе формы нет
    await page.goto(`${WEB}/ru/products/${seed.productSlug}`);
    await page.getByRole("button", { name: /10/ }).first().click();
    await page.getByRole("button", { name: webRu.product.addToRequest }).click();
    await page.goto(`${WEB}/ru/request`);

    const name = page.getByLabel(r.name);
    await name.focus();
    await expect(name).toBeFocused();
    await expectVisibleFocus(page);
    await page.keyboard.press("Tab");
    await expect(page.getByLabel(r.phone)).toBeFocused();
    await expectVisibleFocus(page);
    // до чекбокса согласия и кнопки — без мыши
    for (let i = 0; i < 12 && !(await page.getByRole("checkbox").evaluate((el) => el === document.activeElement)); i++) {
      await page.keyboard.press("Tab");
    }
    await expect(page.getByRole("checkbox")).toBeFocused();
    await expectVisibleFocus(page);
    await page.keyboard.press("Space");
    await expect(page.getByRole("checkbox")).toBeChecked();
  });

  test("фильтры каталога управляются с клавиатуры", async ({ page }) => {
    await page.goto(`${WEB}/ru/products`);
    await page.keyboard.press("Tab");
    // первым Tab-ом открывается ссылка «к содержимому» или шапка — фокус обязан быть видимым
    await expectVisibleFocus(page);
    const search = page.getByRole("searchbox").or(page.getByRole("textbox")).first();
    await search.focus();
    await expectVisibleFocus(page);
  });

  test("админка: вход без мыши", async ({ page }) => {
    const t = adminRu.login;
    await page.addInitScript(() => localStorage.setItem("tb.admin.locale", "ru"));
    await page.goto(`${ADMIN}/`);
    await page.getByLabel(t.loginLabel).focus();
    await expectVisibleFocus(page);
    await page.keyboard.type(ADMIN_EMAIL);
    await page.keyboard.press("Tab");
    await expect(page.getByLabel(t.passwordLabel)).toBeFocused();
    await page.keyboard.type(ADMIN_PASSWORD);
    await page.keyboard.press("Enter");
    await expect(page.getByRole("link", { name: adminRu.nav.warehouse })).toBeVisible();
  });
});

/** Видимый фокус: у активного элемента есть контур или тень фокуса (не «outline: none» без замены). */
async function expectVisibleFocus(page: Page) {
  const style = await page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body) return null;
    const s = getComputedStyle(el);
    return { outline: s.outlineStyle !== "none" && parseFloat(s.outlineWidth) > 0, shadow: s.boxShadow !== "none", tag: el.tagName };
  });
  expect(style, "нет элемента в фокусе").not.toBeNull();
  expect(style!.outline || style!.shadow, `фокус на ${style!.tag} не виден`).toBe(true);
}
