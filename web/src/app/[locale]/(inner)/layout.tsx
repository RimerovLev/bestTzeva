import type { ReactNode } from "react";

/**
 * Внутренние страницы (каталог, товар, заявка, услуги, портфолио, цвета) — в колонке 1200px.
 * Главная живёт вне этой группы: её цветные секции идут во всю ширину. Группа (inner) на URL
 * не влияет.
 */
export default function InnerLayout({ children }: { children: ReactNode }) {
  return <div className="mx-auto w-full max-w-[1200px] px-4 pb-24 pt-8">{children}</div>;
}
