import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/** Адреса изолированного e2e-стека (scripts/stack.sh) и учётка его одноразового админа. */
export const WEB = process.env.E2E_WEB_URL ?? "http://localhost:3100";
export const ADMIN = process.env.E2E_ADMIN_URL ?? "http://localhost:5174";
export const API = process.env.E2E_API_URL ?? "http://localhost:18080";
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "e2e-admin@tzevabest.local";
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "e2e-only-Admin-pass-1";

export const SEED_FILE = fileURLToPath(new URL("./.run/seed.json", import.meta.url));

/** Что создал сид (tests/seed.setup.ts): товар, его варианты и склад. */
export interface Seed {
  productSlug: string;
  productName: string;
  variantSku: string;
  otherVariantSku: string;
  warehouseId: number;
  warehouseCode: string;
}

export function readSeed(): Seed {
  return JSON.parse(readFileSync(SEED_FILE, "utf8")) as Seed;
}

/** Уникальный израильский мобильный на прогон: лимит заявок — 3 в час на номер. */
export function randomPhone(): string {
  const n = String(Math.floor(Math.random() * 10_000_000)).padStart(7, "0");
  return `052-${n.slice(0, 3)}-${n.slice(3)}`;
}
