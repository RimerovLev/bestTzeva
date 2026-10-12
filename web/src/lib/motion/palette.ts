import { ralHex } from "./ral";

/**
 * Цвета для примерки: короткая подборка с названиями, полный RAL Classic (lib/motion/ral.ts) и
 * произвольный цвет из пикера. HEX — экранное приближение RAL; финальный оттенок подтверждается по
 * веерной карте — об этом говорит подпись под калькулятором.
 */
export interface Swatch { code: string; hex: string; /** цвет текста на полосе */ on?: string }

/** Подборка с локализованными названиями (home.colors.studio.names). HEX — из таблицы RAL Classic. */
const CURATED: { code: string; on?: string }[] = [
  { code: "9010" }, { code: "1015" }, { code: "1023", on: "#121212" }, { code: "2004" },
  { code: "3020" }, { code: "4010" }, { code: "4005" }, { code: "5015" },
  { code: "5011" }, { code: "6018" }, { code: "6021", on: "#121212" }, { code: "7016" },
];
export const SWATCHES: Swatch[] = CURATED.map(({ code, on }) => ({ code, hex: ralHex(code) ?? "#cccccc", ...(on ? { on } : {}) }));

/** Полосы над визуализатором — яркая часть подборки. */
export const STRIPES = ["3020", "2004", "1023", "6018", "5015", "4010", "6021", "7016"];

export const DEFAULT_SWATCH = "5015";

const FALLBACK: Swatch = { code: "5015", hex: ralHex("5015") ?? "#3172ad" };
export const swatchByCode = (code: string): Swatch => SWATCHES.find((s) => s.code === code) ?? FALLBACK;

/** Примерные цвета кружков под банками в ленте каталога (у товара в API цвета нет). */
export const RAIL_COLORS = ["#e6d2b5", "#2271b3", "#e8322b", "#57a639", "#ff7ab6", "#7b61ff", "#ffc933", "#2ed1a0"];

/** Расход для быстрой прикидки на главной; точный — в калькуляторе товара (сервер). */
export const COVERAGE_M2_PER_L = 10;
export const CAN_LITERS = 2.5;
