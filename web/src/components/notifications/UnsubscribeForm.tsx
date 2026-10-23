"use client";

import { useState } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface UnsubscribeLabels {
  title: string;
  prompt: string;
  confirm: string;
  submitting: string;
  done: string;
  invalid: string;
  error: string;
}

/**
 * Подтверждение отписки (аудит п.7): GET на бэкенде состояние не меняет, поэтому сама отписка —
 * это POST по кнопке отсюда. Токен берётся из ссылки письма. Нет токена → сообщение об ошибке.
 */
export function UnsubscribeForm({ token, labels }: { token: string | null; labels: UnsubscribeLabels }) {
  const [state, setState] = useState<"idle" | "submitting" | "done" | "error">("idle");

  if (!token) {
    return <p className="unsub-msg unsub-msg--error">{labels.invalid}</p>;
  }
  if (state === "done") {
    return <p className="unsub-msg">{labels.done}</p>;
  }

  async function submit() {
    setState("submitting");
    try {
      const res = await fetch(
        `${API_BASE}/api/v1/notifications/unsubscribe?token=${encodeURIComponent(token!)}`,
        { method: "POST" },
      );
      setState(res.ok ? "done" : "error");
    } catch {
      setState("error");
    }
  }

  return (
    <div className="unsub">
      <p>{labels.prompt}</p>
      <button type="button" className="btn btn-primary" onClick={submit} disabled={state === "submitting"}>
        {state === "submitting" ? labels.submitting : labels.confirm}
      </button>
      {state === "error" && <p className="unsub-msg unsub-msg--error">{labels.error}</p>}
    </div>
  );
}
