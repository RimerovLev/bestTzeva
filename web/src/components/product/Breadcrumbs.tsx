import Link from "next/link";

export interface Crumb {
  label: string;
  href?: string;
}

/** Хлебные крошки над заголовком плашки. Последний элемент — текущая страница (без ссылки). */
export function Breadcrumbs({ items, label }: { items: Crumb[]; label: string }) {
  return (
    <nav aria-label={label} className="crumbs">
      <ol>
        {items.map((item, i) => (
          <li key={i} style={{ display: "contents" }}>
            {i > 0 && <span aria-hidden>/</span>}
            {item.href && i < items.length - 1 ? (
              <Link href={item.href}>{item.label}</Link>
            ) : (
              <span aria-current={i === items.length - 1 ? "page" : undefined}>{item.label}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
