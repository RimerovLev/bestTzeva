import { describe, expect, it } from "vitest";
import { sameContactKey } from "./api";

/** Подтверждение обезличивания: повторный ввод того же контакта в другой записи должен совпасть. */
describe("sameContactKey", () => {
  it("treats local and international forms of one phone as the same", () => {
    const k = sameContactKey("050-111-9999");
    expect(sameContactKey("0501119999")).toBe(k);
    expect(sameContactKey("+972 50 111 9999")).toBe(k);
    expect(sameContactKey(" 972-50-1119999 ")).toBe(k);
  });
  it("tells different phones apart", () => {
    expect(sameContactKey("050-111-9998")).not.toBe(sameContactKey("050-111-9999"));
  });
  it("compares emails case-insensitively but exactly otherwise", () => {
    expect(sameContactKey("Dana@Example.com ")).toBe(sameContactKey("dana@example.com"));
    expect(sameContactKey("dana@example.co")).not.toBe(sameContactKey("dana@example.com"));
  });
});
