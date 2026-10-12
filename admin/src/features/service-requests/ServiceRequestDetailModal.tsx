import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { resolveErrorMessage } from "@/lib/api/messages";
import { StatusBadge } from "./StatusBadge";
import {
  getServiceRequest,
  transitionServiceRequest,
  assignServiceRequest,
  autoAssignServiceRequest,
  addServiceComment,
  scheduleMeasurement,
  createQuote,
  getQuotePdfUrl,
  createPortfolioFromRequest,
  genericTransitions,
  canScheduleMeasurement,
  canQuote,
  normStatus,
  upper,
  type ServiceRequestDetail,
  type ServiceStatus,
} from "./api";

export function ServiceRequestDetailModal({
  id,
  onClose,
  onChanged,
}: {
  id: number;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { dict, locale } = useI18n();
  const t = dict.serviceRequests;
  const { user } = useAuth();
  const qc = useQueryClient();

  const query = useQuery({ queryKey: ["service-request", id], queryFn: () => getServiceRequest(id) });
  const detail = query.data;

  const [comment, setComment] = useState("");
  const [newComment, setNewComment] = useState("");
  const [measureAt, setMeasureAt] = useState("");
  const [quote, setQuote] = useState({ price: "", work_composition: "", term_days: "", comment: "" });
  const [consent, setConsent] = useState(false);
  const [portfolioId, setPortfolioId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function onOk(d: ServiceRequestDetail) {
    qc.setQueryData(["service-request", id], d);
    onChanged();
    setActionError(null);
  }
  function onErr(e: unknown) {
    setActionError(resolveErrorMessage(dict.errors, e).text);
  }
  function refetchDetail() {
    qc.invalidateQueries({ queryKey: ["service-request", id] });
    onChanged();
  }

  const transitionM = useMutation({
    mutationFn: ({ to, c }: { to: ServiceStatus; c?: string }) => transitionServiceRequest(id, to, c),
    onSuccess: (d) => {
      onOk(d);
      setComment("");
    },
    onError: onErr,
  });
  const assignM = useMutation({ mutationFn: (m: number | null) => assignServiceRequest(id, m), onSuccess: onOk, onError: onErr });
  const autoAssignM = useMutation({ mutationFn: () => autoAssignServiceRequest(id), onSuccess: onOk, onError: onErr });
  const measureM = useMutation({
    mutationFn: (iso: string) => scheduleMeasurement(id, iso),
    onSuccess: (d) => {
      onOk(d);
      setMeasureAt("");
    },
    onError: onErr,
  });
  const quoteM = useMutation({
    mutationFn: () =>
      createQuote(id, {
        price: Number(quote.price),
        work_composition: quote.work_composition || undefined,
        term_days: quote.term_days ? Number(quote.term_days) : undefined,
        comment: quote.comment || undefined,
      }),
    onSuccess: (d) => {
      onOk(d);
      setQuote({ price: "", work_composition: "", term_days: "", comment: "" });
    },
    onError: onErr,
  });
  const commentM = useMutation({
    mutationFn: (text: string) => addServiceComment(id, text),
    onSuccess: () => {
      refetchDetail();
      setNewComment("");
      setActionError(null);
    },
    onError: onErr,
  });
  const portfolioM = useMutation({
    mutationFn: () => createPortfolioFromRequest(id),
    onSuccess: (newId) => {
      setPortfolioId(newId ?? null);
      setActionError(null);
    },
    onError: onErr,
  });

  async function openPdf() {
    try {
      const url = await getQuotePdfUrl(id);
      if (url) window.open(url, "_blank", "noopener,noreferrer");
    } catch (e) {
      onErr(e);
    }
  }

  const dateFmt = (iso?: string) => (iso ? new Date(iso).toLocaleString(locale === "he" ? "he-IL" : "ru-RU") : "—");
  const money = (n?: number | null) => (n == null ? "—" : `₪${n.toLocaleString(locale === "he" ? "he-IL" : "ru-RU")}`);
  const status = detail ? normStatus(detail.status) : "NEW";
  const busy = transitionM.isPending || assignM.isPending || autoAssignM.isPending || measureM.isPending || quoteM.isPending || commentM.isPending || portfolioM.isPending;

  return (
    <Modal open onClose={onClose} title={detail ? `${t.title} ${detail.request_number ?? ""}` : "…"}>
      {query.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : query.isError || !detail ? (
        <p className="text-red-700">{t.loadError}</p>
      ) : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={detail.status} />
            <span className="text-sm text-ink-soft">{dateFmt(detail.created_at)}</span>
            {detail.source && <span className="text-xs text-ink-soft">· {detail.source}</span>}
          </div>

          {/* Контакты */}
          <section className="rounded-control border border-line p-3 text-sm">
            <div className="font-medium text-navy">{detail.customer_name}</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {detail.customer_phone && (
                <>
                  <a href={`tel:${detail.customer_phone}`} className="rounded-control bg-navy px-3 py-1.5 text-xs font-medium text-surface hover:bg-navy-deep">
                    {detail.customer_phone}
                  </a>
                  <a
                    href={`https://wa.me/${(detail.customer_phone ?? "").replace(/\D/g, "")}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="rounded-control bg-emerald-600 px-3 py-1.5 text-xs font-medium text-surface hover:bg-emerald-700"
                  >
                    WhatsApp
                  </a>
                </>
              )}
              {detail.customer_email && (
                <a href={`mailto:${detail.customer_email}`} className="rounded-control border border-line px-3 py-1.5 text-xs text-ink">
                  {detail.customer_email}
                </a>
              )}
            </div>
          </section>

          {/* Параметры объекта */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.objectTitle}</h3>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <Row label={t.propertyType} value={dict.enums.property_type[upper(detail.property_type) as keyof typeof dict.enums.property_type] ?? detail.property_type} />
              <Row label={t.area} value={detail.area_sqm != null ? `${detail.area_sqm}` : "—"} />
              <Row label={t.rooms} value={detail.rooms_count != null ? String(detail.rooms_count) : "—"} />
              <Row label={t.ceiling} value={detail.ceiling_height_m != null ? String(detail.ceiling_height_m) : "—"} />
              <Row label={t.city} value={detail.city ?? "—"} />
              <Row label={t.address} value={detail.address ?? "—"} />
              <Row
                label={t.preferredDates}
                value={[detail.preferred_date_from, detail.preferred_date_to].filter(Boolean).join(" — ") || "—"}
              />
            </dl>
            {detail.work_types && detail.work_types.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {detail.work_types.map((w) => (
                  <span key={w} className="rounded-pill border border-line px-2 py-0.5 text-xs text-ink">
                    {dict.enums.work_type[upper(w) as keyof typeof dict.enums.work_type] ?? w}
                  </span>
                ))}
              </div>
            )}
            {detail.comment && (
              <p className="mt-2 text-sm text-ink-soft">
                {t.customerComment}: <span className="text-ink">{detail.comment}</span>
              </p>
            )}
          </section>

          {/* Расчёт */}
          <section className="rounded-control bg-paper-warm p-3 text-sm">
            <div>
              {t.estimateRange}:{" "}
              <span className="font-semibold text-navy">
                {money(detail.estimated_price_min)} – {money(detail.estimated_price_max)}
              </span>
            </div>
            {detail.quoted_price != null && (
              <div className="mt-1 flex items-center gap-2">
                {t.quotedPrice}: <span className="font-semibold text-terracotta">{money(detail.quoted_price)}</span>
                <Button variant="outline" onClick={openPdf}>
                  {t.quotePdfLink}
                </Button>
              </div>
            )}
            {detail.measurement_at && (
              <div className="mt-1 text-ink-soft">
                {t.measurementScheduledAt}: <span className="text-ink">{dateFmt(detail.measurement_at)}</span>
              </div>
            )}
          </section>

          {/* Фото клиента — контрактный пробел */}
          <section>
            <h3 className="mb-1 text-sm font-semibold text-navy">{t.photosTitle}</h3>
            <p className="rounded-control border border-dashed border-line p-3 text-xs text-ink-soft">{t.photosGap}</p>
          </section>

          {actionError && <div className="rounded-control border border-red-200 bg-red-50 p-3 text-sm text-red-700">{actionError}</div>}

          {/* Смена статуса (общие переходы) */}
          {genericTransitions(status).length > 0 && (
            <section>
              <h3 className="mb-2 text-sm font-semibold text-navy">{t.changeStatus}</h3>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder={t.statusCommentPlaceholder}
                rows={2}
                className="mb-2 w-full rounded-control border border-line px-3 py-2 text-sm"
              />
              <div className="flex flex-wrap gap-2">
                {genericTransitions(status).map((to) => (
                  <Button key={to} variant="outline" disabled={busy} onClick={() => transitionM.mutate({ to, c: comment.trim() || undefined })}>
                    {dict.enums.service_status[to]}
                  </Button>
                ))}
              </div>
            </section>
          )}

          {/* Планирование замера */}
          {canScheduleMeasurement(status) && (
            <section className="rounded-control border border-sky-200 bg-sky-50/60 p-3">
              <h3 className="mb-2 text-sm font-semibold text-navy">{t.measurementTitle}</h3>
              <label className="flex flex-col gap-1 text-sm text-ink-soft">
                {t.measurementAt}
                <input
                  type="datetime-local"
                  value={measureAt}
                  onChange={(e) => setMeasureAt(e.target.value)}
                  className="rounded-control border border-line px-3 py-2 text-ink"
                />
              </label>
              <p className="mt-2 text-xs text-sky-700">{t.measurementReminder}</p>
              <Button
                className="mt-2"
                variant="brass"
                disabled={busy || !measureAt}
                onClick={() => measureM.mutate(new Date(measureAt).toISOString())}
              >
                {t.measurementSchedule}
              </Button>
            </section>
          )}

          {/* Котировка */}
          {canQuote(status) && (
            <section className="rounded-control border border-amber-200 bg-amber-50/60 p-3">
              <h3 className="mb-2 text-sm font-semibold text-navy">{t.quoteTitle}</h3>
              <div className="grid grid-cols-2 gap-2">
                <label className="flex flex-col gap-1 text-sm text-ink-soft">
                  {t.quotePrice}
                  <input type="number" value={quote.price} onChange={(e) => setQuote({ ...quote, price: e.target.value })} className="rounded-control border border-line px-3 py-2 text-ink" />
                </label>
                <label className="flex flex-col gap-1 text-sm text-ink-soft">
                  {t.quoteTerm}
                  <input type="number" value={quote.term_days} onChange={(e) => setQuote({ ...quote, term_days: e.target.value })} className="rounded-control border border-line px-3 py-2 text-ink" />
                </label>
              </div>
              <label className="mt-2 flex flex-col gap-1 text-sm text-ink-soft">
                {t.quoteComposition}
                <textarea value={quote.work_composition} onChange={(e) => setQuote({ ...quote, work_composition: e.target.value })} rows={2} className="rounded-control border border-line px-3 py-2 text-ink" />
              </label>
              <label className="mt-2 flex flex-col gap-1 text-sm text-ink-soft">
                {t.quoteComment}
                <input value={quote.comment} onChange={(e) => setQuote({ ...quote, comment: e.target.value })} className="rounded-control border border-line px-3 py-2 text-ink" />
              </label>
              <p className="mt-2 text-xs text-amber-700">{t.quoteGeneratedNote}</p>
              <Button className="mt-2" variant="brass" disabled={busy || !quote.price} onClick={() => quoteM.mutate()}>
                {t.quoteIssue}
              </Button>
            </section>
          )}

          {/* Создание портфолио из завершённой заявки */}
          {status === "COMPLETED" && (
            <section className="rounded-control border border-emerald-200 bg-emerald-50/60 p-3">
              <h3 className="mb-1 text-sm font-semibold text-navy">{t.portfolioTitle}</h3>
              <p className="mb-2 text-xs text-ink-soft">{t.portfolioNote}</p>
              {portfolioId != null ? (
                <p className="text-sm font-medium text-emerald-700">{t.portfolioCreated.replace("{id}", String(portfolioId))}</p>
              ) : (
                <>
                  <label className="mb-2 flex items-center gap-2 text-sm text-ink">
                    <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                    {t.portfolioConsent}
                  </label>
                  <Button variant="brass" disabled={busy || !consent} onClick={() => portfolioM.mutate()}>
                    {t.portfolioCreate}
                  </Button>
                </>
              )}
            </section>
          )}

          {/* Назначение менеджера */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.assignment}</h3>
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-ink-soft">
                {detail.assigned_manager_id != null ? t.managerId.replace("{id}", String(detail.assigned_manager_id)) : t.unassigned}
              </span>
              {user && detail.assigned_manager_id !== user.id && (
                <Button variant="outline" disabled={busy} onClick={() => assignM.mutate(user.id)}>
                  {t.assignToMe}
                </Button>
              )}
              <Button variant="outline" disabled={busy} onClick={() => autoAssignM.mutate()}>
                {t.autoAssign}
              </Button>
              {detail.assigned_manager_id != null && (
                <Button variant="muted" disabled={busy} onClick={() => assignM.mutate(null)}>
                  {t.unassign}
                </Button>
              )}
            </div>
            <p className="mt-1 text-xs text-ink-soft">{t.assignNote}</p>
          </section>

          {/* История */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.history}</h3>
            <ul className="space-y-1 text-sm">
              {(detail.history ?? []).map((h, i) => (
                <li key={i} className="flex flex-wrap gap-2 text-ink-soft">
                  <span className="text-ink">
                    {h.from_status ? `${statusLabel(dict, h.from_status)} → ` : ""}
                    {statusLabel(dict, h.to_status)}
                  </span>
                  <span>{dateFmt(h.created_at)}</span>
                  {h.comment && <span className="italic">«{h.comment}»</span>}
                </li>
              ))}
            </ul>
          </section>

          {/* Комментарии */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.comments}</h3>
            {(detail.comments ?? []).length === 0 ? (
              <p className="text-sm text-ink-soft">{t.noComments}</p>
            ) : (
              <ul className="mb-3 space-y-2 text-sm">
                {detail.comments!.map((c) => (
                  <li key={c.id} className="rounded-control border border-line p-2">
                    <div className="text-ink">{c.body}</div>
                    <div className="mt-1 text-xs text-ink-soft">{dateFmt(c.created_at)}</div>
                  </li>
                ))}
              </ul>
            )}
            <textarea
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder={t.commentPlaceholder}
              rows={2}
              className="mb-2 w-full rounded-control border border-line px-3 py-2 text-sm"
            />
            <Button variant="outline" disabled={busy || !newComment.trim()} onClick={() => commentM.mutate(newComment.trim())}>
              {t.send}
            </Button>
          </section>
        </div>
      )}
    </Modal>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="inline text-ink-soft">{label}: </dt>
      <dd className="inline text-ink">{value}</dd>
    </div>
  );
}

function statusLabel(dict: ReturnType<typeof useI18n>["dict"], raw?: string): string {
  const key = normStatus(raw);
  return dict.enums.service_status[key] ?? raw ?? "";
}
