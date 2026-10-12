"use client";

import { useState } from "react";
import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { useRequest } from "@/lib/request/RequestProvider";
import type { ProductRequestResponse } from "@/lib/catalog/api";
import { RequestItemsPanel } from "./RequestItemsPanel";
import { RequestForm } from "./RequestForm";

export function RequestView({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const r = dict.request;
  const request = useRequest();
  const [unavailableIds, setUnavailableIds] = useState<Set<number>>(new Set());
  const [success, setSuccess] = useState<ProductRequestResponse | null>(null);

  if (success) {
    return (
      <div className="mx-auto max-w-md rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <p className="text-h2 font-bold text-navy">{r.successTitle}</p>
        <p className="mt-4 text-sm text-ink-soft">{r.successNumber}</p>
        <p className="numeric text-h1 font-bold text-brass">
          <bdi>{success.request_number}</bdi>
        </p>
        <p className="mt-2 text-ink-soft">{r.successBody}</p>
        <div className="mt-6 flex justify-center gap-3">
          <Link
            href={`/${locale}/products`}
            className="rounded-control border border-line-strong px-4 py-2 text-sm text-ink-soft hover:bg-paper-warm"
          >
            {r.toCatalog}
          </Link>
          <button
            type="button"
            onClick={() => setSuccess(null)}
            className="rounded-control bg-navy px-4 py-2 text-sm text-surface hover:bg-navy-deep"
          >
            {r.newRequest}
          </button>
        </div>
      </div>
    );
  }

  if (request.items.length === 0) {
    return (
      <div className="rounded-card border border-line bg-surface p-12 text-center shadow-card">
        <p className="text-h3 text-navy">{r.emptyTitle}</p>
        <p className="mt-2 text-ink-soft">{r.emptyBody}</p>
        <Link
          href={`/${locale}/products`}
          className="mt-4 inline-block rounded-control border border-brass px-4 py-2 text-sm text-brass hover:bg-brass hover:text-surface"
        >
          {r.toCatalog}
        </Link>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
      <RequestItemsPanel
        items={request.items}
        locale={locale}
        dict={dict}
        unavailableIds={unavailableIds}
        onQty={request.setQty}
        onRemove={(id) => {
          request.remove(id);
          setUnavailableIds((prev) => {
            const next = new Set(prev);
            next.delete(id);
            return next;
          });
        }}
      />
      <RequestForm
        items={request.items}
        locale={locale}
        dict={dict}
        onUnavailable={(ids) => setUnavailableIds(new Set(ids))}
        onSuccess={(res) => {
          trackSubmitted(res.request_number);
          request.clear();
          setSuccess(res);
        }}
      />
    </div>
  );
}

/** Событие в аналитику. Реальная интеграция (GA4/Plausible) — в F-5.2; здесь безопасный dataLayer-push. */
function trackSubmitted(requestNumber?: string): void {
  try {
    const w = window as unknown as { dataLayer?: unknown[] };
    w.dataLayer = w.dataLayer ?? [];
    w.dataLayer.push({ event: "product_request_submitted", request_number: requestNumber });
  } catch {
    // аналитика не должна ломать поток
  }
}
