import { describe, expect, it, vi } from "vitest";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "./problem";
import { fieldErrors, resolveErrorMessage } from "./messages";
import { unwrap } from "./http";

const messages = {
  "request.variant_unavailable": "Этого варианта сейчас нет",
  [CLIENT_ERROR_CODES.unknown]: "Произошла ошибка",
};

function response(status: number, headers: Record<string, string> = {}) {
  return new Response(null, { status, headers });
}

describe("разбор ошибок API (RFC 9457)", () => {
  it("узнаёт problem+json только по строковому code", () => {
    expect(isProblemDetail({ code: "x" })).toBe(true);
    expect(isProblemDetail({ code: 1 })).toBe(false);
    expect(isProblemDetail(null)).toBe(false);
    expect(isProblemDetail("oops")).toBe(false);
  });

  it("unwrap отдаёт данные при успехе", () => {
    expect(unwrap({ data: { id: 1 }, response: response(200) })).toEqual({ id: 1 });
  });

  it("unwrap превращает problem в ApiError с полями, trace_id и Retry-After", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    const err = (() => {
      try {
        unwrap({
          error: { code: "common.rate_limited", detail: "slow down", trace_id: "t-1", errors: { phone: ["bad"] } },
          response: response(429, { "Retry-After": "30" }),
        });
      } catch (e) {
        return e;
      }
    })() as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe("common.rate_limited");
    expect(err.status).toBe(429);
    expect(err.retryAfter).toBe(30);
    expect(err.traceId).toBe("t-1");
    expect(spy).toHaveBeenCalledWith(expect.stringContaining("t-1")); // trace_id попадает в консоль для поддержки
    spy.mockRestore();
  });

  it("ответ без problem-тела — ApiError client.unknown со статусом ответа", () => {
    expect(() => unwrap({ error: "<html>502</html>", response: response(502) })).toThrowError(
      expect.objectContaining({ code: CLIENT_ERROR_CODES.unknown, status: 502 }),
    );
  });

  it("мусорный Retry-After не превращается в NaN", () => {
    try {
      unwrap({ error: { code: "x" }, response: response(503, { "Retry-After": "soon" }) });
    } catch (e) {
      expect((e as ApiError).retryAfter).toBeUndefined();
    }
  });
});

describe("текст ошибки для пользователя", () => {
  it("свой текст по коду важнее серверного detail", () => {
    const e = new ApiError({ code: "request.variant_unavailable", status: 409, detail: "server text", traceId: "t" });
    expect(resolveErrorMessage(messages, e)).toEqual({ text: "Этого варианта сейчас нет", traceId: "t" });
  });

  it("нет своего текста — серверный detail, нет и его — общий запасной", () => {
    expect(resolveErrorMessage(messages, new ApiError({ code: "new.code", status: 400, detail: "detail" })).text).toBe("detail");
    expect(resolveErrorMessage(messages, new ApiError({ code: "new.code", status: 400 })).text).toBe("Произошла ошибка");
  });

  it("не-ApiError (сбой в коде) не показывает пользователю стек", () => {
    expect(resolveErrorMessage(messages, new TypeError("x is undefined")).text).toBe("Произошла ошибка");
  });

  it("ошибки полей — первое сообщение на поле", () => {
    const e = new ApiError({ code: "common.validation_error", status: 422, errors: { phone: ["a", "b"], name: [] } });
    expect(fieldErrors(e)).toEqual({ phone: "a" });
    expect(fieldErrors(new Error("x"))).toEqual({});
  });
});
