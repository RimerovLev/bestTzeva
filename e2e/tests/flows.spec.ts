import { expect, test, type Page } from "@playwright/test";
import webRu from "../../web/src/dictionaries/ru.json" with { type: "json" };
import adminRu from "../../admin/src/dictionaries/ru.json" with { type: "json" };
import { ADMIN, ADMIN_EMAIL, ADMIN_PASSWORD, WEB, randomPhone, readSeed } from "../env";

/** Ячейки строки склада по заголовкам колонок: физический остаток, резерв, доступно. */
async function stockOf(page: Page, sku: string) {
  const t = adminRu.inventory;
  const headers = (await page.getByRole("columnheader").allInnerTexts()).map((h) => h.trim());
  const row = page.getByRole("row").filter({ hasText: sku });
  await expect(row).toHaveCount(1);
  const cell = async (title: string) => Number((await row.getByRole("cell").nth(headers.indexOf(title)).innerText()).trim());
  return { physical: await cell(t.colPhysical), reserved: await cell(t.colReserved), available: await cell(t.colAvailable) };
}

/**
 * Главный путь продаж целиком, против настоящего бэкенда: гость собирает заявку на витрине →
 * сотрудник входит в админку, приходует товар и резервирует позиции именно этой заявки.
 * Тексты кнопок и полей берутся из словарей — тест ломается от смены поведения, а не формулировки.
 */
test.describe.serial("заявка на товар: витрина → склад → резерв", () => {
  let requestNumber = "";

  test("гость добавляет вариант в заявку и отправляет её", async ({ page }) => {
    const seed = readSeed();
    const p = webRu.product;
    const r = webRu.request;

    await page.goto(`${WEB}/ru/products/${seed.productSlug}`);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(seed.productName);

    // 10 л — единственный вариант с остатком; 3 л без остатка добавить нельзя
    await page.getByRole("button", { name: /10/ }).first().click();
    await page.getByRole("button", { name: p.addToRequest }).click();
    await expect(page.getByRole("button", { name: p.inRequest })).toBeDisabled();
    await page.getByRole("link", { name: p.toRequest }).click();

    await expect(page).toHaveURL(/\/ru\/request$/);
    await page.getByLabel(r.name).fill("Дана Леви");
    await page.getByLabel(r.phone).fill(randomPhone());
    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: r.submit }).click();

    await expect(page.getByText(r.successTitle)).toBeVisible();
    requestNumber = (await page.locator(".num-big").innerText()).trim();
    expect(requestNumber).toMatch(/^PR-/);

    // после отправки список заявки очищен
    await page.reload();
    await expect(page.getByRole("button", { name: r.submit })).toHaveCount(0);
  });

  test("сотрудник входит, проводит приход и резервирует позиции заявки", async ({ page }) => {
    test.skip(!requestNumber, "нет заявки из предыдущего шага");
    const seed = readSeed();
    const t = adminRu;

    await page.addInitScript(() => localStorage.setItem("tb.admin.locale", "ru"));
    await page.goto(`${ADMIN}/`);
    await page.getByLabel(t.login.loginLabel).fill(ADMIN_EMAIL);
    await page.getByLabel(t.login.passwordLabel).fill(ADMIN_PASSWORD);
    await page.getByRole("button", { name: t.login.submit }).click();
    await expect(page.getByRole("link", { name: t.nav.warehouse })).toBeVisible();

    // Приход: +5 к стартовому остатку 2
    await page.getByRole("link", { name: t.nav.warehouse }).click();
    await page.getByRole("button", { name: t.inventory.receipt, exact: true }).click();
    const dialog = page.getByRole("dialog", { name: t.inventory.receiptTitle });
    await dialog.getByPlaceholder(t.inventory.sku).fill(seed.variantSku);
    await dialog.getByPlaceholder(t.inventory.qty).fill("5");
    await dialog.getByRole("button", { name: t.inventory.apply }).click();
    await expect(dialog).toBeHidden();
    await expect.poll(() => stockOf(page, seed.variantSku)).toEqual({ physical: 7, reserved: 0, available: 7 });

    // Заявка с витрины: «Связались» → «Зарезервировано» с подтверждением
    await page.getByRole("link", { name: t.nav.productRequests }).click();
    await page.getByRole("button", { name: requestNumber }).click();
    const card = page.getByRole("dialog");
    await card.getByRole("button", { name: t.enums.request_status.CONTACTED, exact: true }).click();
    await expect(card.getByText(t.enums.request_status.CONTACTED).first()).toBeVisible();
    await card.getByRole("button", { name: t.enums.request_status.RESERVED, exact: true }).click();
    await card.getByRole("button", { name: t.productRequests.reserveConfirm }).click();
    await expect(card.getByRole("button", { name: t.productRequests.reserveConfirm })).toBeHidden();
    await expect(card.getByText(t.enums.request_status.RESERVED).first()).toBeVisible();

    // Резерв виден на складе: физически 7, в резерве 1, доступно 6
    await page.keyboard.press("Escape");
    await page.getByRole("link", { name: t.nav.warehouse }).click();
    await expect.poll(() => stockOf(page, seed.variantSku)).toEqual({ physical: 7, reserved: 1, available: 6 });
  });
});
