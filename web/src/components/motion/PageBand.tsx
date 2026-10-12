import type { CSSProperties, ReactNode } from "react";

export type BandTone = "yellow" | "blue" | "red" | "mint" | "violet" | "pink" | "paper";
export type BandTitle = { a: string; hl?: string; b?: string };

/**
 * Цветная плашка-заголовок внутренней страницы: во всю ширину, под шапкой, капает в контент.
 * Ключевые слова заголовка подчёркиваются маркером (прокрашивается при появлении — RevealEffects
 * на странице не нужен: у плашки маркер включён сразу классом .in).
 */
export function PageBand({
  tone,
  eyebrow,
  title,
  body,
  crumbs,
  children,
  product = false,
  dx = "-200px",
}: {
  tone: BandTone;
  eyebrow?: string;
  title: BandTitle | string;
  body?: string | null;
  crumbs?: ReactNode;
  children?: ReactNode;
  /** длинное имя товара — заголовок помельче */
  product?: boolean;
  dx?: string;
}) {
  const t = typeof title === "string" ? { a: title } : title;
  const hlc = tone === "red" ? "var(--ink)" : tone === "yellow" || tone === "mint" || tone === "pink" ? "var(--paper)" : "var(--yellow)";
  return (
    <section className={`sec s-${tone} drip in band full-bleed`} style={{ "--dx": dx } as CSSProperties}>
      <div className="wrap">
        {crumbs}
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1 className={`h-sec${product ? " h-prod" : ""}`}>
          {t.a}
          {t.hl && (
            <>
              {" "}
              <span className="hl in" style={{ "--hlc": hlc, ...(tone === "blue" || tone === "violet" ? { color: "var(--ink)" } : {}) } as CSSProperties}>
                {t.hl}
              </span>
            </>
          )}
          {t.b ? ` ${t.b}` : ""}
        </h1>
        {body && <p className="lead">{body}</p>}
        {children}
      </div>
    </section>
  );
}
