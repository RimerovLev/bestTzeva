import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";
import webRu from "../../web/src/dictionaries/ru.json" with { type: "json" };
import { API, WEB, randomPhone } from "../env";

const PHOTO = fileURLToPath(new URL("../fixtures/room.jpg", import.meta.url));

/**
 * Заявка на замер: четыре шага, фото сжимается в браузере и уходит в приватное хранилище по
 * presigned-ссылке (MinIO из docker-compose), бэкенд получает ключ объекта.
 */
test("форма замера с загрузкой фото доходит до номера заявки", async ({ page }) => {
  const m = webRu.measure;
  const s = webRu.services;
  const r = webRu.request;

  await page.goto(`${WEB}/ru/services/measure`);

  await page.getByLabel(s.area).fill("42");
  await page.getByRole("button", { name: m.next }).click();

  await page.getByRole("group", { name: s.workTypes }).getByRole("button", { name: webRu.enums.work_type.WALLS }).click();
  await page.getByRole("button", { name: m.next }).click();

  await page.getByLabel(r.name, { exact: true }).fill("Михаил Коэн");
  await page.getByLabel(r.phone).fill(randomPhone());
  await page.getByLabel(m.city).fill("Хайфа");
  await page.getByRole("button", { name: m.next }).click();

  const presign = page.waitForResponse((res) => res.url().startsWith(API) && /presign/.test(res.url()) && res.ok());
  const upload = page.waitForResponse((res) => res.request().method() === "PUT" && res.ok());
  await page.locator('input[type="file"]').setInputFiles(PHOTO);
  await presign;
  await upload;
  await expect(page.locator(".ph img")).toHaveCount(1);
  await expect(page.locator(".ph-err")).toHaveCount(0);

  await page.getByRole("checkbox").check();
  const submit = page.getByRole("button", { name: m.submit });
  await expect(submit).toBeEnabled();
  const created = page.waitForResponse((res) => res.url().startsWith(API) && res.request().method() === "POST" && res.status() === 201);
  await submit.click();
  const body = (await (await created).json()) as { request_number?: string };

  await expect(page.getByText(m.successTitle)).toBeVisible();
  await expect(page.locator(".num-big")).toHaveText(body.request_number ?? "");
  expect(body.request_number).toMatch(/^SR-/);
});
