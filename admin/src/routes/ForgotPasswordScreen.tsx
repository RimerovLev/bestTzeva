import { useState, type FormEvent } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { api, unwrap } from "@/lib/api/http";

/**
 * Запрос ссылки для сброса пароля (аудит п.2). POST /api/v1/auth/password/forgot {login}. Ответ
 * одинаков независимо от существования аккаунта — показываем нейтральное сообщение.
 */
export function ForgotPasswordScreen({ onBack }: { onBack: () => void }) {
  const { dict } = useI18n();
  const [loginId, setLoginId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      unwrap(await api.POST("/api/v1/auth/password/forgot", { body: { login: loginId.trim() } }));
    } catch {
      // не раскрываем существование аккаунта — сообщение одинаково и при ошибке
    } finally {
      setSubmitting(false);
      setDone(true);
    }
  }

  return (
    <div className="grid h-full place-items-center bg-paper p-4">
      <div className="w-full max-w-sm space-y-4 rounded-card border border-line bg-surface p-8 shadow-card">
        <h1 className="text-center text-h2 font-bold text-navy">{dict.password.forgotTitle}</h1>
        {done ? (
          <p className="text-center text-body text-ink">{dict.password.forgotDone}</p>
        ) : (
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <Field
              label={dict.password.forgotLabel}
              type="text"
              autoComplete="username"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              required
            />
            <Button type="submit" variant="primary" className="w-full" disabled={submitting}>
              {submitting ? dict.password.submitting : dict.password.forgotSubmit}
            </Button>
          </form>
        )}
        <button type="button" onClick={onBack} className="w-full text-center text-sm text-brass hover:underline">
          {dict.password.back}
        </button>
      </div>
    </div>
  );
}
