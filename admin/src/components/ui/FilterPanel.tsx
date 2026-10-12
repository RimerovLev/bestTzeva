import type { ReactNode } from "react";

interface FilterPanelProps {
  title?: string;
  onReset?: () => void;
  resetLabel?: string;
  children: ReactNode;
}

/**
 * Контейнер панели фильтров: рамка карточки, заголовок и кнопка сброса. Наполнение (поля, селекты,
 * чекбоксы) передаётся детьми. Раскладка — flex-wrap, чтобы одинаково работать в RTL и LTR.
 */
export function FilterPanel({ title, onReset, resetLabel, children }: FilterPanelProps) {
  return (
    <div className="rounded-card border border-line bg-surface p-4 shadow-card">
      {(title || onReset) && (
        <div className="mb-3 flex items-center justify-between">
          {title && <h3 className="text-sm font-semibold text-ink-soft">{title}</h3>}
          {onReset && (
            <button
              type="button"
              onClick={onReset}
              className="text-xs text-brass hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
            >
              {resetLabel}
            </button>
          )}
        </div>
      )}
      <div className="flex flex-wrap items-end gap-3">{children}</div>
    </div>
  );
}
