"use client";

import { useState } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface VerifyEmailLabels {
  prompt: string;
  confirm: string;
  submitting: string;
  done: string;
  invalid: string;
  error: string;
}

/**
 * Подтверждение email (аудит п.14): GET на бэкенде состояние не меняет, подтверждение — POST по
 * кнопке отсюда, чтобы сканеры ссылок из писем не «съедали» одноразовый токен.
 */
export function VerifyEmailForm({ token, labels }: { token: string | null; labels: VerifyEmailLabels }) {
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
      const res = await fetch(`${API_BASE}/api/v1/auth/email/verify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token }),
      });
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
