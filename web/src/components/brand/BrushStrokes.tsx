/**
 * Декоративные живописные мазки из макета (navy/terracotta/brass/sage на кремовой бумаге).
 * Чисто оформление — aria-hidden, не мешает чтению. Позиционируется через className родителя.
 * Мягкость даёт feGaussianBlur — эффект краски, а не геометрии.
 */
export function BrushStrokes({ className = "" }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 520 320"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
      focusable="false"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <filter id="bs-soft" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="4" />
        </filter>
      </defs>
      <g filter="url(#bs-soft)" strokeLinecap="round" fill="none">
        <path d="M20 210 C120 120 210 300 320 170 C400 80 470 150 510 90" stroke="var(--sage)" strokeWidth="34" opacity="0.55" />
        <path d="M0 150 C130 60 240 200 360 110 C440 50 500 110 520 60" stroke="var(--terracotta)" strokeWidth="26" opacity="0.7" />
        <path d="M30 250 C160 190 250 250 380 200 C450 172 500 210 515 175" stroke="var(--navy)" strokeWidth="20" opacity="0.6" />
        <path d="M60 110 C180 40 300 120 420 70 C470 48 505 78 518 55" stroke="var(--brass)" strokeWidth="12" opacity="0.85" />
      </g>
    </svg>
  );
}
