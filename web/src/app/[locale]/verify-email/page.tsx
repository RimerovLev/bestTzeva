import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { VerifyEmailForm } from "@/components/notifications/VerifyEmailForm";

export const metadata: Metadata = { robots: { index: false, follow: false } };

/** Подтверждение email (аудит п.14): ссылка из письма ведёт сюда, подтверждение — POST по кнопке. */
export default async function VerifyEmailPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ token?: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const { token } = await searchParams;
  const dict = await getDictionary(locale);
  const t = dict.verifyEmail;

  return (
    <section className="unsub-page">
      <h1>{t.title}</h1>
      <VerifyEmailForm
        token={token ?? null}
        labels={{
          prompt: t.prompt,
          confirm: t.confirm,
          submitting: t.submitting,
          done: t.done,
          invalid: t.invalid,
          error: t.error,
        }}
      />
    </section>
  );
}
