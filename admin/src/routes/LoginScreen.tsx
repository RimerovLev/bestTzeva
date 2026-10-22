import { useState, type FormEvent } from "react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { fieldErrors, resolveErrorMessage, type ResolvedError } from "@/lib/api/messages";
import { ForgotPasswordScreen } from "@/routes/ForgotPasswordScreen";

/**
 * Экран входа. Вход по email или телефону. Ошибки — по кодам RFC 9457: общий текст из словаря,
 * пофайловые ошибки подсвечивают конкретные поля. trace_id мелким шрифтом для поддержки.
 */
export function LoginScreen() {
  const { dict } = useI18n();
  const { login } = useAuth();

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<ResolvedError | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [forgot, setForgot] = useState(false);

  if (forgot) {
    return <ForgotPasswordScreen onBack={() => setForgot(false)} />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);
    setFields({});
    try {
      await login(loginId.trim(), password);
    } catch (error) {
      setFormError(resolveErrorMessage(dict.errors, error));
      setFields(fieldErrors(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid h-full place-items-center bg-paper p-4">
      <form
        onSubmit={onSubmit}
        className="w-full max-w-sm space-y-4 rounded-card border border-line bg-surface p-8 shadow-card"
        noValidate
      >
        <div className="text-center">
          <div className="mx-auto grid h-11 w-11 place-items-center rounded-control bg-brass font-bold text-navy-deep">
            ב
          </div>
          <h1 className="mt-3 text-h2 font-bold text-navy">{dict.login.title}</h1>
        </div>

        <Field
          label={dict.login.loginLabel}
          type="text"
          autoComplete="username"
          value={loginId}
          onChange={(e) => setLoginId(e.target.value)}
          placeholder={dict.login.loginPlaceholder}
          error={fields.login}
          required
        />
        <Field
          label={dict.login.passwordLabel}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fields.password}
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
          {submitting ? dict.login.submitting : dict.login.submit}
        </Button>

        <button
          type="button"
          onClick={() => setForgot(true)}
          className="block w-full text-center text-sm text-brass hover:underline"
        >
          {dict.login.forgotLink}
        </button>
      </form>
    </div>
  );
}
