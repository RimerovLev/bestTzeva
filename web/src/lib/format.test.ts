import { describe, expect, it } from "vitest";
import { formatPrice, formatPriceRange } from "./format";

/** Убираем неразрывные пробелы-разделители, чтобы сравнивать цифры, а не вид пробела. */
const plain = (s: string) => s.replace(/[\u00a0\u202f]/g, " ");

describe("форматирование цен", () => {
  it("иврит: разделитель тысяч запятая, знак шекеля спереди", () => {
    expect(formatPrice("he", 1249)).toBe("₪1,249");
    expect(formatPrice("he", 89.9)).toBe("₪89.9");
  });

  it("русский: разделитель тысяч пробел, дробная часть через запятую", () => {
    expect(plain(formatPrice("ru", 1249))).toBe("₪1 249");
    expect(formatPrice("ru", 89.9)).toBe("₪89,9");
  });

  it("не больше двух знаков после запятой — цену считает сервер, клиент только показывает", () => {
    expect(formatPrice("he", 10.125)).toBe("₪10.13"); // 10.125 точно представимо — честная проверка округления
    expect(formatPrice("he", 10)).toBe("₪10");
  });

  it("строка без управляющих символов направления — их ставит <bdi> в разметке", () => {
    expect(formatPrice("he", 1249)).not.toMatch(/[\u200e\u200f\u202a-\u202e\u2066-\u2069]/);
  });

  it("диапазон: одно значение при равных ценах, иначе через тире", () => {
    expect(formatPriceRange("he", 120, 120)).toBe("₪120");
    expect(formatPriceRange("he", 120, 1249)).toBe("₪120–₪1,249");
  });
});
