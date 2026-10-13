import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { expect, test as setup } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, API, SEED_FILE, type Seed } from "../env";

/**
 * Сид e2e-базы через настоящий admin API: категория, товар с двумя вариантами, склад и стартовый
 * остаток (без остатка вариант нельзя добавить в заявку на витрине). Бренд вставляет stack.sh —
 * админского API для брендов нет. Коды фиксированные: прогон всегда начинается с чистой базы
 * (`stack.sh reset`), а скриншоты не должны зависеть от номера прогона.
 */
setup("сид: товар, склад, стартовый остаток", async ({ request }) => {
  const brandId = Number(readFileSync(fileURLToPath(new URL("../.run/brand_id", import.meta.url)), "utf8").trim());
  expect(brandId).toBeGreaterThan(0);

  const login = await request.post(`${API}/api/v1/auth/login`, { data: { login: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  expect(login.ok(), await login.text()).toBeTruthy();
  const { access_token: token } = (await login.json()) as { access_token: string };
  const headers = { Authorization: `Bearer ${token}` };

  const post = async <T>(path: string, data: unknown): Promise<T> => {
    const res = await request.post(`${API}/api/v1/admin${path}`, { headers, data });
    expect(res.ok(), `${path}: ${res.status()} ${await res.text()}`).toBeTruthy();
    return (await res.json()) as T;
  };

  const category = await post<{ id: number }>("/categories", {
    slug: "e2e-interior",
    active: true,
    translations: { he: { name: "צבע פנים" }, ru: { name: "Интерьерные краски" } },
  });

  const sku = "E2E-VELVET";
  const product = await post<{ id: number; slug: string }>("/products", {
    category_id: category.id,
    brand_id: brandId,
    sku,
    paint_type: "INTERIOR",
    usage_area: "INDOOR",
    finish: "MATTE",
    base_type: "WATER",
    coverage_sqm_per_liter: 10,
    drying_time_hours: 2,
    active: true,
    translations: {
      he: { name: "קטיפה מט לקירות", short_description: "צבע פנים מט, עמיד לשטיפה" },
      ru: { name: "Бархат матовый для стен", short_description: "Матовая интерьерная краска, моющаяся" },
    },
    variants: [
      { sku: `${sku}-W10`, color_code: "RAL9010", color_hex: "#f1ece1", volume_liters: 10, price: 249 },
      { sku: `${sku}-W3`, color_code: "RAL9010", color_hex: "#f1ece1", volume_liters: 3, price: 89.9 },
    ],
  });

  const warehouseCode = "E2E-MAIN";
  const warehouse = await post<{ id: number }>("/warehouses", {
    code: warehouseCode,
    name_translations: { he: "מחסן ראשי", ru: "Основной склад" },
  });
  await post("/inventory/receipt", { warehouse_id: warehouse.id, comment: "e2e: стартовый остаток", lines: [{ sku: `${sku}-W10`, qty: 2 }] });

  const seed: Seed = {
    productSlug: product.slug,
    productName: "Бархат матовый для стен",
    variantSku: `${sku}-W10`,
    otherVariantSku: `${sku}-W3`,
    warehouseId: warehouse.id,
    warehouseCode,
  };
  writeFileSync(SEED_FILE, JSON.stringify(seed, null, 2));
});
