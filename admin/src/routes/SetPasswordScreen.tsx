import { useMemo, useState, type FormEvent } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { api, unwrap } from "@/lib/api/http";
import { fieldErrors, resolveErrorMessage, type ResolvedError } from "@/lib/api/messages";

/**
 * Публичный экран задания пароля по одноразовому токену из письма (аудит п.2/3). Одна форма на два
 * пути: `/reset-password` (сброс забытого) и `/set-password` (приглашение сотрудника) — различаются
 * только заголовком. POST /api/v1/auth/password/reset {token, new_password}. Доступен без сессии
 * (рендерится в AppGate до разводки по аутентификации).
 */
export function SetPasswordScreen() {
  const { dict } = useI18n();
  const invite = window.location.pathname.startsWith("/set-password");
  const token = useMemo(() => new URLSearchParams(window.location.search).get("token") ?? "", []);

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<ResolvedError | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [done, setDone] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFields({});
    if (password !== confirm) {
      setFields({ new_password: dict.password.mismatch });
      return;
    }
    setSubmitting(true);
    try {
      unwrap(await api.POST("/api/v1/auth/password/reset", { body: { token, new_password: password } }));
      setDone(true);
    } catch (error) {
      setFormError(resolveErrorMessage(dict.errors, error));
      setFields(fieldErrors(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid h-full place-items-center bg-paper p-4">
      <div className="w-full max-w-sm space-y-4 rounded-card border border-line bg-surface p-8 shadow-card">
        <div className="text-center">
          <div className="mx-auto grid h-11 w-11 place-items-center rounded-control bg-brass font-bold text-navy-deep">
            ב
          </div>
          <h1 className="mt-3 text-h2 font-bold text-navy">
            {invite ? dict.password.setTitle : dict.password.resetTitle}
          </h1>
        </div>

        {done ? (
          <div className="space-y-4 text-center">
            <p className="text-body text-ink">{dict.password.done}</p>
            <Button variant="primary" className="w-full" onClick={() => window.location.assign("/")}>
              {dict.password.toLogin}
            </Button>
          </div>
        ) : !token ? (
          <p className="rounded-control border border-crit-fg/30 bg-crit-bg px-3 py-2 text-sm text-crit-fg">
            {dict.password.tokenMissing}
          </p>
        ) : (
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <Field
              label={dict.password.newPassword}
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={fields.new_password}
              required
            />
            <Field
              label={dict.password.confirm}
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
            {formError && (
              <div className="rounded-control border border-crit-fg/30 bg-crit-bg px-3 py-2 text-sm text-crit-fg">
                <p>{formError.text}</p>
                {formError.traceId && (
                  <p className="mt-1 text-xs text-crit-fg/70">
                    {dict.errors.traceLabel}: <bdi className="numeric">{formError.traceId}</bdi>
                  </p>
                )}
              </div>
            )}
            <Button type="submit" variant="primary" className="w-full" disabled={submitting}>
              {submitting ? dict.password.submitting : dict.password.submit}
            </Button>
          </form>
        )}
      </div>
    </div>
  );
}
