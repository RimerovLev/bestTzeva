import { Dela_Gothic_One, Rubik, Onest, Heebo, JetBrains_Mono } from "next/font/google";

/**
 * Шрифты через next/font: self-host, subset и display=swap — так они не блокируют
 * отрисовку и не дают CLS. Каждый экспонируется CSS-переменной.
 *
 * Пары собраны в стеки (globals.css): латиницу и кириллицу рисует первая гарнитура, иврит —
 * вторая. Ни Dela Gothic One, ни Onest не несут иврит, а unicode-range у subset'ов заставляет
 * браузер брать ивритские глифы из следующей гарнитуры стека — переключать семейство по :lang()
 * не нужно, и «RAL 5015» внутри ивритской строки остаётся в фирменной гарнитуре.
 *
 * Предзагружаются только ивритские гарнитуры (основная локаль). Все восемь файлов с высоким
 * приоритетом отнимали сеть у первого экрана — LCP на мобильном ~3.9 с (Lighthouse, F-11.1).
 * Остальные грузятся по swap: текст сразу виден запасным шрифтом с подогнанными метриками.
 */

// Дисплей: заголовки, ленты, цены. Латиница + кириллица.
export const dela = Dela_Gothic_One({
  subsets: ["latin", "cyrillic"],
  weight: "400",
  display: "swap",
  preload: false,
  variable: "--font-dela",
});

// Дисплей для иврита: Rubik 800 — ближайший по плотности к Dela Gothic.
export const rubik = Rubik({
  subsets: ["hebrew"],
  weight: ["800"],
  display: "swap",
  variable: "--font-rubik",
});

// Текст: латиница + кириллица.
export const onest = Onest({
  subsets: ["latin", "cyrillic"],
  weight: ["400", "500", "600", "700"],
  display: "swap",
  preload: false,
  variable: "--font-onest",
});

// Текст на иврите.
export const heebo = Heebo({
  subsets: ["hebrew"],
  weight: ["400", "500", "700"],
  display: "swap",
  variable: "--font-heebo",
});

// Подписи, коды RAL, артикулы, номера заявок.
export const mono = JetBrains_Mono({
  subsets: ["latin", "cyrillic"],
  weight: ["400", "500", "700"],
  display: "swap",
  preload: false,
  variable: "--font-mono",
});

/** Классы всех переменных — вешаются на <html>, чтобы токены были доступны везде. */
export const fontVariables = [dela.variable, rubik.variable, onest.variable, heebo.variable, mono.variable].join(" ");
