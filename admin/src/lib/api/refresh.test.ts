import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { refreshOnce } from "./http";
import { clearAccessToken, getAccessToken } from "./tokens";

/**
 * Готово-критерий F-6.1: параллельные 401 вызывают ОДИН refresh, а не пять. Проверяем, что
 * несколько одновременных refreshOnce() коалесцируются в один сетевой вызов /auth/refresh.
 */
describe("single-flight refresh", () => {
  beforeEach(() => clearAccessToken());
  afterEach(() => vi.unstubAllGlobals());

  it("coalesces parallel refreshes into one network call", async () => {
    let calls = 0;
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });

    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        calls += 1;
        await gate; // держим все параллельные вызовы в одном окне
        return new Response(JSON.stringify({ access_token: "fresh-token" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }),
    );

    const inFlight = Promise.all([refreshOnce(), refreshOnce(), refreshOnce(), refreshOnce(), refreshOnce()]);
    release();
    const results = await inFlight;

    expect(calls).toBe(1);
    expect(results).toEqual([true, true, true, true, true]);
    expect(getAccessToken()).toBe("fresh-token");
  });

  it("starts a new call after the previous one settled", async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response(JSON.stringify({ access_token: "t" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await refreshOnce();
    await refreshOnce();

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
