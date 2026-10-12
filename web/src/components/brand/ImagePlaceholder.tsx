/**
 * Пустое окно под будущее фото + видимый промт для генерации прямо под рамкой. Строительные леса:
 * дизайнер/владелец видит, ГДЕ картинка и ЧТО сгенерировать. Когда фото готово — меняем на next/image.
 * ratio — соотношение сторон (CSS aspect-ratio), поэтому вёрстка не «прыгает» и без картинки.
 */
export function ImagePlaceholder({
  ratio = "4 / 3",
  label,
  prompt,
  className = "",
}: {
  ratio?: string;
  label: string;
  prompt: string;
  className?: string;
}) {
  return (
    <figure className={`overflow-hidden rounded-card border border-line bg-surface/70 backdrop-blur-sm ${className}`}>
      <div
        className="relative grid place-items-center border-b border-dashed border-line-strong bg-paper-warm/50"
        style={{ aspectRatio: ratio }}
      >
        <div className="flex flex-col items-center gap-2 px-4 text-center">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden className="text-line-strong">
            <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.5" />
            <circle cx="8.5" cy="9.5" r="1.6" stroke="currentColor" strokeWidth="1.5" />
            <path d="M4 17l4.5-4.5 3.5 3.5 3-3 5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <span className="text-sm font-medium text-ink-soft">{label}</span>
        </div>
      </div>
      <figcaption className="p-3 text-start text-xs leading-relaxed text-ink-soft">
        <span className="font-semibold uppercase tracking-wide text-brass">Prompt · </span>
        <span dir="ltr" className="select-all">{prompt}</span>
      </figcaption>
    </figure>
  );
}
