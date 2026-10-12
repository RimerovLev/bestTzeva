import Link from "next/link";

export interface Crumb {
  label: string;
  href?: string;
}

/** Хлебные крошки. Последний элемент — текущая страница (без ссылки). Разделитель зеркалится в RTL. */
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="breadcrumb" className="mb-4 text-sm text-ink-soft">
      <ol className="flex flex-wrap items-center gap-1">
        {items.map((item, i) => (
          <li key={i} className="flex items-center gap-1">
            {i > 0 && <span aria-hidden className="text-line-strong">/</span>}
            {item.href && i < items.length - 1 ? (
              <Link href={item.href} className="transition-colors hover:text-navy">
                {item.label}
              </Link>
            ) : (
              <span className={i === items.length - 1 ? "text-ink" : undefined} aria-current={i === items.length - 1 ? "page" : undefined}>
                {item.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
