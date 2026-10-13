import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Download, ShieldAlert } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { anonymizeSubject, exportSubject, sameContactKey, type SubjectExport } from "./api";

/** Секции выгрузки в порядке показа (ключи ответа сервера). */
const SECTIONS = [
  "product_requests",
  "product_request_items",
  "service_requests",
  "service_request_media",
  "consents",
  "notifications",
] as const;

/**
 * Приватность (F-10.2, только ADMIN): запрос субъекта данных по телефону или email — выгрузка
 * всего, что о нём хранится, и обезличивание. Обезличивание необратимо, поэтому идёт в два шага:
 * отдельное окно, где контакт нужно набрать ещё раз руками (вставка отключена) и отметить, что
 * последствия понятны. Оба действия сервер пишет в журнал аудита.
 */
export function PrivacyPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const t = dict.privacy;

  const [contact, setContact] = useState("");
  const [exported, setExported] = useState<{ contact: string; data: SubjectExport } | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [retyped, setRetyped] = useState("");
  const [understood, setUnderstood] = useState(false);
  const [result, setResult] = useState<{ contact: string; count: number } | null>(null);

  const exportM = useMutation({
    mutationFn: (c: string) => exportSubject(c),
    onSuccess: (data, c) => setExported({ contact: c, data }),
  });
  const anonymizeM = useMutation({
    mutationFn: (c: string) => anonymizeSubject(c),
    onSuccess: (count, c) => {
      setResult({ contact: c, count });
      setExported(null);
      closeConfirm();
    },
  });

  if (!user || !canAccessPath(user.role, "/privacy")) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  const value = contact.trim();
  const matches = retyped.trim() !== "" && sameContactKey(retyped) === sameContactKey(value);

  function closeConfirm() {
    setConfirmOpen(false);
    setRetyped("");
    setUnderstood(false);
    anonymizeM.reset();
  }

  function download() {
    if (!exported) return;
    const blob = new Blob([JSON.stringify(exported.data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `subject-export-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  const rows = (k: string) => {
    const v = exported?.data[k];
    return Array.isArray(v) ? v.length : 0;
  };
  const total = exported ? SECTIONS.reduce((n, k) => n + rows(k), 0) : 0;

  return (
    <div className="max-w-3xl space-y-5">
      <div>
        <h1 className="text-h2 font-bold text-navy">{t.title}</h1>
        <p className="mt-1 text-sm text-ink-soft">{t.subtitle}</p>
      </div>

      <section className="space-y-4 rounded-card border border-line bg-surface p-5 shadow-card">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (value) {
              setResult(null);
              exportM.mutate(value);
            }
          }}
        >
          <div className="min-w-[16rem] flex-1">
            <Field
              label={t.contact}
              placeholder={t.contactPlaceholder}
              value={contact}
              dir="ltr"
              autoComplete="off"
              onChange={(e) => {
                setContact(e.target.value);
                setExported(null);
                exportM.reset();
              }}
            />
          </div>
          <Button type="submit" variant="primary" disabled={!value || exportM.isPending}>
            {exportM.isPending ? dict.common.loading : t.find}
          </Button>
        </form>
        <p className="text-xs text-ink-soft">{t.auditNote}</p>
        {exportM.isError && <p className="text-sm text-crit-fg">{resolveErrorMessage(dict.errors, exportM.error).text}</p>}
      </section>

      {exported && (
        <section className="space-y-4 rounded-card border border-line bg-surface p-5 shadow-card">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-h3 font-bold text-navy">
              {t.found} <bdi dir="ltr" className="numeric">{exported.contact}</bdi>
            </h2>
            <Button variant="outline" onClick={download} disabled={total === 0}>
              <Download className="h-4 w-4" aria-hidden /> {t.download}
            </Button>
          </div>
          {total === 0 ? (
            <p className="text-sm text-ink-soft">{t.nothing}</p>
          ) : (
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {SECTIONS.map((k) => (
                <div key={k} className="rounded-control border border-line p-3">
                  <dt className="text-xs text-ink-soft">{t.sections[k]}</dt>
                  <dd className="numeric mt-1 text-h3 font-bold text-navy">{rows(k)}</dd>
                </div>
              ))}
            </dl>
          )}

          {total > 0 && (
            <div className="rounded-control border border-crit-fg/30 bg-crit-bg p-4">
              <h3 className="flex items-center gap-2 font-semibold text-crit-fg">
                <ShieldAlert className="h-4 w-4" aria-hidden /> {t.anonymizeTitle}
              </h3>
              <p className="mt-1 text-sm text-ink">{t.anonymizeBody}</p>
              <Button variant="outline" className="mt-3 !border-crit-fg !text-crit-fg hover:!bg-crit-fg hover:!text-surface" onClick={() => setConfirmOpen(true)}>
                {t.anonymizeStart}
              </Button>
            </div>
          )}
        </section>
      )}

      {result && (
        <p role="status" className="rounded-card border border-line bg-surface p-4 text-sm shadow-card">
          {result.count > 0 ? t.doneCount.replace("{n}", String(result.count)) : t.doneNone}{" "}
          <bdi dir="ltr" className="numeric">{result.contact}</bdi>
        </p>
      )}

      <Modal
        open={confirmOpen}
        onClose={closeConfirm}
        title={t.confirmTitle}
        footer={
          <>
            <Button variant="muted" onClick={closeConfirm}>{dict.common.cancel}</Button>
            <Button
              variant="primary"
              className="!bg-crit-fg hover:!bg-crit-fg/90"
              disabled={!matches || !understood || anonymizeM.isPending}
              onClick={() => anonymizeM.mutate(value)}
            >
              {anonymizeM.isPending ? dict.common.loading : t.confirmAction}
            </Button>
          </>
        }
      >
        <div className="space-y-4 text-sm">
          <p>{t.confirmBody}</p>
          <ul className="list-disc space-y-1 ps-5 text-ink-soft">
            <li>{t.confirmKeep}</li>
            <li>{t.confirmGone}</li>
          </ul>
          <Field
            label={t.retype.replace("{contact}", value)}
            value={retyped}
            dir="ltr"
            autoComplete="off"
            onPaste={(e) => e.preventDefault()}
            onDrop={(e) => e.preventDefault()}
            onChange={(e) => setRetyped(e.target.value)}
            error={retyped.trim() && !matches ? t.retypeMismatch : undefined}
          />
          <label className="flex items-start gap-2">
            <input type="checkbox" className="mt-1" checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
            <span>{t.understand}</span>
          </label>
          {anonymizeM.isError && <p className="text-crit-fg">{resolveErrorMessage(dict.errors, anonymizeM.error).text}</p>}
        </div>
      </Modal>
    </div>
  );
}
