import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "brass" | "outline" | "muted";

const VARIANTS: Record<Variant, string> = {
  // тёмно-синяя — основное действие, одна на экран
  primary: "bg-navy text-surface hover:bg-navy-deep disabled:opacity-50",
  // латунная — действие в карточке/строке
  brass: "bg-brass text-surface hover:bg-navy disabled:opacity-50",
  // контурная латунная — второстепенное
  outline: "border border-brass text-brass hover:bg-brass hover:text-surface disabled:opacity-50",
  // приглушённая — сброс/отмена
  muted: "bg-paper-warm text-ink-soft hover:bg-line disabled:opacity-50",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  children: ReactNode;
}

export function Button({ variant = "primary", className = "", type = "button", ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-control px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta focus-visible:ring-offset-1 disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      {...rest}
    />
  );
}
