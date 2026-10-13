import { StrictMode, type ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { RequestProvider, useRequest, type RequestItem } from "./RequestProvider";

const KEY = "tb.request.items";

const item = (variantId: number): Omit<RequestItem, "qty"> => ({
  productId: 1,
  slug: "velvet",
  variantId,
  name: "Velvet",
  colorCode: "RAL9010",
  colorHex: "#f1ece1",
  volumeLiters: 10,
  price: 249,
});

function setup(strict = false) {
  const wrapper = ({ children }: { children: ReactNode }) =>
    strict ? (
      <StrictMode>
        <RequestProvider>{children}</RequestProvider>
      </StrictMode>
    ) : (
      <RequestProvider>{children}</RequestProvider>
    );
  return renderHook(() => useRequest(), { wrapper });
}

const stored = (): RequestItem[] => JSON.parse(localStorage.getItem(KEY) ?? "[]");

afterEach(() => localStorage.clear());

describe("список заявки", () => {
  it("добавляет позицию с количеством 1 и не дублирует тот же вариант", () => {
    const { result } = setup();
    act(() => result.current.add(item(10)));
    act(() => result.current.add(item(10)));
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0]!.qty).toBe(1);
    expect(result.current.has(10)).toBe(true);
    expect(result.current.has(11)).toBe(false);
  });

  it("количество не опускается ниже 1", () => {
    const { result } = setup();
    act(() => result.current.add(item(10)));
    act(() => result.current.setQty(10, 0));
    expect(result.current.items[0]!.qty).toBe(1);
    act(() => result.current.setQty(10, 5));
    expect(result.current.items[0]!.qty).toBe(5);
  });

  it("удаляет позицию и очищает список", () => {
    const { result } = setup();
    act(() => {
      result.current.add(item(10));
      result.current.add(item(11));
    });
    act(() => result.current.remove(10));
    expect(result.current.items.map((i) => i.variantId)).toEqual([11]);
    act(() => result.current.clear());
    expect(result.current.count).toBe(0);
    expect(stored()).toEqual([]);
  });

  it("сохраняет список в localStorage", () => {
    const { result } = setup();
    act(() => result.current.add(item(10)));
    expect(stored().map((i) => i.variantId)).toEqual([10]);
  });

  it("поднимает сохранённый список после перезагрузки, в том числе в StrictMode", async () => {
    localStorage.setItem(KEY, JSON.stringify([{ ...item(10), qty: 3 }]));
    const { result } = setup(true);
    await waitFor(() => expect(result.current.count).toBe(1));
    expect(result.current.items[0]!.qty).toBe(3);
    expect(stored()).toHaveLength(1); // пустой стартовый список не затёр сохранённый
  });

  it("битые данные в хранилище не роняют страницу", async () => {
    localStorage.setItem(KEY, "{not json");
    const { result } = setup();
    await waitFor(() => expect(result.current.count).toBe(0));
  });
});
