/**
 * Реквизиты компании для структурированных данных (LocalBusiness). Задаются переменными окружения:
 * пока заказчик их не дал, соответствующие поля в разметку не попадают — заглушки вроде «+972 00…»
 * в Google уходить не должны. Формат часов — schema.org openingHours, например «Su-Th 08:00-18:00».
 */
const env = (name: string) => process.env[name]?.trim() || undefined;

export const BUSINESS = {
  phone: env("NEXT_PUBLIC_BUSINESS_PHONE"),
  email: env("NEXT_PUBLIC_BUSINESS_EMAIL"),
  street: env("NEXT_PUBLIC_BUSINESS_STREET"),
  city: env("NEXT_PUBLIC_BUSINESS_CITY"),
  postalCode: env("NEXT_PUBLIC_BUSINESS_POSTAL_CODE"),
  hours: env("NEXT_PUBLIC_BUSINESS_HOURS"),
  /** Где работаем: страна по умолчанию, города через запятую — если задано. */
  areaServed: env("NEXT_PUBLIC_BUSINESS_AREA")?.split(",").map((s) => s.trim()).filter(Boolean),
};
