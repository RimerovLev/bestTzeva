/**
 * Геройская лента каталога: крупный заголовок, английский подзаголовок и ромбовидный разделитель.
 * Фон-мрамор общий (в layout), поэтому свои мазки/градиент убраны — текст с кремовым ореолом.
 */
export function CatalogHero({ title, titleEn, subtitle }: { title: string; titleEn: string; subtitle: string }) {
  return (
    <header className="mb-8 py-6">
      <h1 className="display text-display font-bold leading-tight text-navy-deep">{title}</h1>
      <p className="mt-1 text-sm font-medium uppercase tracking-[0.15em] text-brass">{titleEn}</p>
      <div className="my-4 flex items-center gap-2 text-brass">
        <span className="h-px w-10 bg-brass/50" />
        <span aria-hidden className="text-xs">◆</span>
        <span className="h-px w-24 bg-brass/25" />
      </div>
      <p className="max-w-xl text-ink font-medium">{subtitle}</p>
    </header>
  );
}
