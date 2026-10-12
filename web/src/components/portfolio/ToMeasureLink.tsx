"use client";

import { useRouter } from "next/navigation";
import type { Locale } from "@/lib/i18n/config";
import { MEASURE_PREFILL_KEY } from "@/lib/services/api";

/** Переход к заявке на замер с предзаполненным типом объекта из проекта портфолио (F-4.3 п.5). */
export function ToMeasureLink({
  locale,
  propertyType,
  label,
}: {
  locale: Locale;
  propertyType?: string;
  label: string;
}) {
  const router = useRouter();
  function go() {
    try {
      if (propertyType) {
        sessionStorage.setItem(
          MEASURE_PREFILL_KEY,
          JSON.stringify({ property_type: propertyType.toUpperCase() }),
        );
      }
    } catch {
      /* приватный режим */
    }
    router.push(`/${locale}/services/measure`);
  }
  return (
    <button
      type="button"
      onClick={go}
      className="inline-flex items-center gap-2 rounded-pill bg-terracotta px-6 py-3 font-semibold text-surface shadow-card transition hover:bg-terracotta-deep"
    >
      {label}
    </button>
  );
}
