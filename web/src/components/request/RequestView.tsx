"use client";

import { useState } from "react";
import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { useRequest } from "@/lib/request/RequestProvider";
import type { ProductRequestResponse } from "@/lib/catalog/api";
import { RequestItemsPanel } from "./RequestItemsPanel";
import { RequestForm } from "./RequestForm";
import { track } from "@/lib/analytics";
import { BRIGHT, useMotion } from "@/components/motion/MotionProvider";

export function RequestView({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const r = dict.request;
  const request = useRequest();
  const [unavailableIds, setUnavailableIds] = useState<Set<number>>(new Set());
  const [success, setSuccess] = useState<ProductRequestResponse | null>(null);
  const { burst } = useMotion();

  if (success) {
    return (
      <div className="card ok done" role="status">
        <span className="ok-ico">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M20 6 9 17l-5-5" /></svg>
        </span>
        <h3>{r.successTitle}</h3>
        <p>{r.successNumber}: <bdi className="num-big">{success.request_number}</bdi></p>
        <p>{r.successBody}</p>
        <div className="row">
          <Link href={`/${locale}/products`} className="btn btn-sm">{r.toCatalog}</Link>
          <button type="button" onClick={() => setSuccess(null)} className="btn btn-yellow btn-sm">{r.newRequest}</button>
        </div>
      </div>
    );
  }

  if (request.items.length === 0) {
    return (
      <div className="card empty">
        <h3>{r.emptyTitle}</h3>
        <p>{r.emptyBody}</p>
        <div className="row">
          <Link href={`/${locale}/products`} className="btn btn-red btn-sm">{r.toCatalog}</Link>
          <Link href={`/${locale}#colors`} className="btn btn-sm">{r.tryColor}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="req">
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
        onSuccess={(res, at) => {
          track("Product Request Submitted", { items: request.items.length, locale });
          if (at) burst(at.x, at.y, 80, BRIGHT, 1.35);
          request.clear();
          setSuccess(res);
        }}
      />
    </div>
  );
}
