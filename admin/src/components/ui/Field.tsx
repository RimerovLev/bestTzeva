import { useId, type InputHTMLAttributes } from "react";

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Ошибка поля — обычно из errors[имя_поля] ответа RFC 9457 (см. fieldErrors). */
  error?: string;
}

/**
 * Поле формы: подпись, инпут, сообщение об ошибке. Ошибка связывается с полем через
 * aria-describedby и aria-invalid — важно для доступности (аудитория включает пожилых).
 */
export function Field({ label, error, id, className = "", ...rest }: FieldProps) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const errorId = `${fieldId}-error`;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={fieldId} className="text-sm font-medium text-ink-soft">
        {label}
      </label>
      <input
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`rounded-control border bg-surface px-3 py-2 text-body text-ink outline-none transition-colors focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta ${
          error ? "border-crit-fg" : "border-line-strong"
        } ${className}`}
        {...rest}
      />
      {error && (
        <p id={errorId} className="text-xs text-crit-fg">
          {error}
        </p>
      )}
    </div>
  );
}
