"use client";

import { useRef } from "react";
import { useMotion } from "./MotionProvider";

/** Телефон крупно + «Скопировать». Без доступа к буферу — выделяем номер, чтобы скопировать руками. */
export function CopyPhone({ phone, tel, copy, copied, selected }: { phone: string; tel: string; copy: string; copied: string; selected: string }) {
  const { say } = useMotion();
  const ref = useRef<HTMLAnchorElement>(null);
  const select = () => {
    const el = ref.current;
    if (!el) return;
    const r = document.createRange();
    r.selectNodeContents(el);
    const s = getSelection();
    s?.removeAllRanges();
    s?.addRange(r);
    say(selected);
  };
  return (
    <div className="phone-row">
      <a className="phone" ref={ref} href={`tel:${tel}`} dir="ltr">{phone}</a>
      <button
        className="copy"
        type="button"
        onClick={() => {
          try { navigator.clipboard.writeText(phone).then(() => say(copied), select); } catch { select(); }
        }}
      >
        {copy}
      </button>
    </div>
  );
}
