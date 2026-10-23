import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { UnsubscribeForm } from "@/components/notifications/UnsubscribeForm";

export const metadata: Metadata = { robots: { index: false, follow: false } };

/** Страница отписки от рассылки (аудит п.7): ссылка из письма ведёт сюда, отписка — POST по кнопке. */
export default async function UnsubscribePage({
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
  const t = dict.unsubscribe;

  return (
    <section className="unsub-page">
      <h1>{t.title}</h1>
      <UnsubscribeForm
        token={token ?? null}
        labels={{
          title: t.title,
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
