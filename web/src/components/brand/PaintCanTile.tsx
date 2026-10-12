/**
 * Плитка-плейсхолдер товара: стилизованная банка краски на тёплом фоне. В ответе каталога нет ни
 * фото, ни цвета варианта (ProductSummary их не отдаёт — см. заметку контракта), поэтому банка
 * декоративная: акцент выбирается детерминированно по seed (id), чтобы сетка играла цветом, но не
 * выдавала конкретный оттенок краски за настоящий. Когда появятся реальные фото — заменим на next/image.
 */
const ACCENTS = ["var(--navy)", "var(--terracotta)", "var(--brass)", "var(--sage)"];

export function PaintCanTile({ seed = 0, className = "" }: { seed?: number; className?: string }) {
  const accent = ACCENTS[Math.abs(seed) % ACCENTS.length];
  return (
    <div className={`relative flex items-center justify-center overflow-hidden bg-gradient-to-br from-paper-warm to-sage-pale ${className}`}>
      <svg viewBox="0 0 120 120" className="h-3/4 w-3/4" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden focusable="false">
        {/* тень */}
        <ellipse cx="60" cy="104" rx="34" ry="6" fill="var(--navy)" opacity="0.08" />
        {/* корпус */}
        <path d="M30 44 h60 v46 a30 8 0 0 1 -60 0 z" fill="var(--surface)" stroke="var(--line-strong)" strokeWidth="2" />
        {/* цветная этикетка */}
        <path d="M30 60 h60 v18 a30 6 0 0 1 -60 0 z" fill={accent} opacity="0.9" />
        {/* верхний обод */}
        <ellipse cx="60" cy="44" rx="30" ry="8" fill="var(--paper)" stroke="var(--line-strong)" strokeWidth="2" />
        <ellipse cx="60" cy="43" rx="21" ry="5" fill={accent} opacity="0.85" />
        {/* дужка */}
        <path d="M34 40 C40 18 80 18 86 40" stroke="var(--brass)" strokeWidth="3" fill="none" strokeLinecap="round" />
        {/* капля краски */}
        <path d="M60 78 c5 8 5 13 0 17 c-5 -4 -5 -9 0 -17 z" fill={accent} />
      </svg>
    </div>
  );
}
