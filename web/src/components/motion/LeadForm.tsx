"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import type { Locale } from "@/lib/i18n/config";
import { resolveErrorMessage, type ErrorMessages } from "@/lib/api/messages";
import { getFormToken, submitServiceRequest, type ServiceRequestCreate } from "@/lib/services/api";
import { BRIGHT, useMotion } from "./MotionProvider";

type PropertyType = ServiceRequestCreate["property_type"];

export interface LeadFormText {
  name: string;
  namePlaceholder: string;
  phone: string;
  phonePlaceholder: string;
  need: string;
  needs: { value: PropertyType; label: string }[];
  comment: string;
  commentPlaceholder: string;
  consent: string;
  policy: string;
  required: string;
  submit: string;
  submitting: string;
  okTitle: string;
  okNumber: string;
  okBody: string;
  again: string;
}

/**
 * Короткая заявка «перезвоните мне» внизу главной → POST /service-requests (тот же контракт, что
 * у заявки на замер: form_token, honeypot website, согласие). Телефон уходит как набран — нормализует
 * сервер. Выбранный вариант «что нужно» дополнительно пишется в комментарий: OFFICE/COMMERCIAL/OTHER
 * в списке склеены, менеджеру нужна формулировка клиента.
 */
export function LeadForm({ locale, text, errors }: { locale: Locale; text: LeadFormText; errors: ErrorMessages }) {
  const { burst } = useMotion();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [need, setNeed] = useState(0);
  const [comment, setComment] = useState("");
  const [consent, setConsent] = useState(false);
  const [website, setWebsite] = useState("");
  const [token, setToken] = useState("");
  const [invalid, setInvalid] = useState<{ name?: boolean; phone?: boolean }>({});
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const tokenAsked = useRef(false);
  const submitRef = useRef<HTMLButtonElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const phoneRef = useRef<HTMLInputElement>(null);

  // токен берём при первом касании формы: он же меряет минимальное время заполнения
  const askToken = () => {
    if (tokenAsked.current) return;
    tokenAsked.current = true;
    getFormToken(locale).then(setToken).catch(() => { tokenAsked.current = false; });
  };
  useEffect(() => { setDone(null); }, [locale]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const badName = name.trim().length < 2, badPhone = phone.replace(/\D/g, "").length < 9;
    setInvalid({ name: badName, phone: badPhone });
    if (badName || badPhone) {
      setError(text.required);
      (badName ? nameRef : phoneRef).current?.focus();
      return;
    }
    if (!consent) { setError(errors["request.consent_required"] ?? text.required); return; }
    setSending(true);
    setError(null);
    try {
      const chosen = text.needs[need];
      const body: ServiceRequestCreate = {
        customer_name: name.trim(),
        customer_phone: phone,
        property_type: chosen?.value ?? "OTHER",
        comment: [chosen?.label, comment.trim()].filter(Boolean).join(" — "),
        consent,
        website,
        form_token: token || undefined,
      };
      const res = await submitServiceRequest(body, locale);
      setDone(res.request_number ?? "");
      const r = submitRef.current?.getBoundingClientRect();
      if (r) burst(r.left + r.width / 2, r.top + r.height / 2, 80, BRIGHT, 1.35);
    } catch (err) {
      setError(resolveErrorMessage(errors, err).text);
    } finally {
      setSending(false);
    }
  }

  if (done !== null) {
    return (
      <div className="ok">
        <span className="ok-ico">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M20 6 9 17l-5-5" /></svg>
        </span>
        <h3>{text.okTitle}</h3>
        {done && <p>{text.okNumber}: <bdi className="num-big">{done}</bdi></p>}
        <p>{text.okBody}</p>
        <button
          className="btn btn-yellow"
          type="button"
          onClick={() => { setDone(null); setName(""); setPhone(""); setComment(""); setConsent(false); tokenAsked.current = false; setToken(""); }}
        >
          {text.again}
        </button>
      </div>
    );
  }

  return (
    <form className="tb-form" noValidate onSubmit={onSubmit} onFocus={askToken}>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="lead-name">{text.name}</label>
          <input ref={nameRef} id="lead-name" name="name" autoComplete="name" placeholder={text.namePlaceholder} value={name} aria-invalid={invalid.name || undefined} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="lead-phone">{text.phone}</label>
          <input ref={phoneRef} id="lead-phone" name="phone" type="tel" autoComplete="tel" inputMode="tel" dir="ltr" placeholder={text.phonePlaceholder} value={phone} aria-invalid={invalid.phone || undefined} onChange={(e) => setPhone(e.target.value)} />
        </div>
      </div>
      <div className="field">
        <label htmlFor="lead-need">{text.need}</label>
        <select id="lead-need" value={need} onChange={(e) => setNeed(Number(e.target.value))}>
          {text.needs.map((n, i) => <option key={i} value={i}>{n.label}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor="lead-comment">{text.comment}</label>
        <textarea id="lead-comment" rows={3} placeholder={text.commentPlaceholder} value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
      {/* honeypot: не display:none — часть ботов такие поля пропускает */}
      <div className="hp" aria-hidden>
        <label>Website<input type="text" tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} /></label>
      </div>
      <label className="consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>{text.consent} <Link href={`/${locale}/privacy`}>{text.policy}</Link></span>
      </label>
      {error && <p className="form-err" role="alert">{error}</p>}
      <button ref={submitRef} className="btn btn-red" type="submit" disabled={sending} style={{ justifySelf: "start" }}>
        {sending ? text.submitting : text.submit}
        <svg className="icon-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M5 12h14M13 6l6 6-6 6" /></svg>
      </button>
    </form>
  );
}
