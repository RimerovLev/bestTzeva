"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import type { Locale } from "@/lib/i18n/config";
import {
  CAN_LITERS, COVERAGE_M2_PER_L, DEFAULT_SWATCH, STRIPES, SWATCHES, swatchByCode,
} from "@/lib/motion/palette";
import { ColorEngine } from "@/lib/motion/colorEngine";
import { renderRoom } from "@/lib/motion/renderApi";
import { useMotion } from "./MotionProvider";
import { ColorPicker } from "./ColorPicker";
import { RAL_CLASSIC, nearestRal, ralHex } from "@/lib/motion/ral";

type PluralForms = { one: string; few: string; many: string; other: string };

export interface ColorStudioText {
  stripesLabel: string;
  names: Record<string, string>;
  canvasAlt: string;
  before: string;
  after: string;
  hint: string;
  drop: string;
  busy: string;
  steps: string[];
  upload: string;
  privacy: string;
  render: string;
  rerender: string;
  renderHint: string;
  needRender: string;
  paletteLabel: string;
  tabs: { set: string; ral: string; any: string };
  ralSearch: string;
  ralNone: string;
  customColor: string;
  nearest: string;
  pickerSquare: string;
  pickerHue: string;
  pickerNote: string;
  demo: string;
  area: string;
  coats: string;
  coatOptions: string[];
  calcResult: string;
  cans: PluralForms;
  calcEmpty: string;
  calcNote: string;
  toastRendered: string;
  toastRenderFailed: string;
  toastNotConfigured: string;
  toastRateLimited: string;
  toastDemo: string;
  toastUploaded: string;
  toastBadFile: string;
  toastOpenError: string;
}

const NUMBER_LOCALE: Record<Locale, string> = { he: "he-IL", ru: "ru-RU" };
/** Пример: та же гостиная с белыми стенами. */
const DEMO_SRC = "/assets/motion/demo-white.webp";

/**
 * Примерка цвета на ИИ: фото комнаты уходит на бэкенд (OpenAI), возвращается фотореалистичный
 * рендер со стенами выбранного цвета. Один платный вызов на комнату; дальше смена цвета —
 * локальная (в браузере, по маске), без новых вызовов. Холст — в LTR: координаты не зависят от языка.
 */
export function ColorStudio({ locale, text }: { locale: Locale; text: ColorStudioText }) {
  const { say, reduce } = useMotion();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<ColorEngine | null>(null);
  const renderAbort = useRef<AbortController | null>(null);

  /** Выбранный цвет: HEX и код RAL (null — произвольный цвет из пикера). */
  const [hex, setHex] = useState(() => swatchByCode(DEFAULT_SWATCH).hex);
  const [code, setCode] = useState<string | null>(DEFAULT_SWATCH);
  const [tab, setTab] = useState<"set" | "ral" | "any">("set");
  const [ralQuery, setRalQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [hasRender, setHasRender] = useState(false);
  const [compare, setCompare] = useState(false);
  const [hint, setHint] = useState<"demo" | "need" | "hidden">("demo");
  const [dragging, setDragging] = useState(false);
  const [area, setArea] = useState("14");
  const [coats, setCoats] = useState(2);

  const textRef = useRef(text);
  textRef.current = text;
  const hexRef = useRef(hex);
  hexRef.current = hex;

  // движок + демо-фото (просто показываем комнату, без рендера)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const engine = new ColorEngine(canvas, {
      onBusy: setBusy,
      onHasRender: setHasRender,
      onError: () => say(textRef.current.toastOpenError),
    });
    engineRef.current = engine;
    const io = new IntersectionObserver(([en]) => {
      if (!en?.isIntersecting) return;
      io.disconnect();
      void engine.load(DEMO_SRC);
    }, { rootMargin: "600px 0px" });
    io.observe(canvas);
    return () => { io.disconnect(); engine.dispose(); engineRef.current = null; };
  }, [say]);

  /** Выбор цвета: если уже есть рендер — перекрашиваем локально сразу; иначе подсказываем «Перекрасить». */
  const applyHex = useCallback((nextHex: string, nextCode: string | null) => {
    setHex(nextHex);
    setCode(nextCode);
    const engine = engineRef.current;
    if (!engine) return;
    if (compare) { setCompare(false); engine.showOriginal(false); }
    if (engine.hasRender) engine.recolor(nextHex);
    else setHint((h) => (h === "hidden" ? h : "need"));
  }, [compare]);
  const pick = useCallback((nextCode: string) => applyHex(ralHex(nextCode) ?? swatchByCode(nextCode).hex, nextCode), [applyHex]);

  // пикер шлёт цвет на каждое движение: образец сразу, локальную перекраску — когда рука замерла
  const pickerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pickAny = useCallback((nextHex: string) => {
    setHex(nextHex);
    setCode(null);
    const engine = engineRef.current;
    if (compare) { setCompare(false); engine?.showOriginal(false); }
    if (!engine?.hasRender) { setHint((h) => (h === "hidden" ? h : "need")); return; }
    if (pickerTimer.current) clearTimeout(pickerTimer.current);
    pickerTimer.current = setTimeout(() => engine.recolor(nextHex), 160);
  }, [compare]);
  useEffect(() => () => { if (pickerTimer.current) clearTimeout(pickerTimer.current); }, []);

  /** Перекрасить через ИИ: один платный вызов. Результат показываем и готовим к локальной смене цвета. */
  const doRender = useCallback(async () => {
    const engine = engineRef.current;
    if (!engine || busy) return;
    const blob = await engine.originalBlob();
    if (!blob) return;
    renderAbort.current?.abort();
    const ac = new AbortController();
    renderAbort.current = ac;
    setBusy(true);
    const nm = textRef.current;
    const name = code ? (nm.names[code] ?? `RAL ${code}`) : "";
    try {
      const res = await renderRoom(blob, hexRef.current, name, ac.signal);
      if (ac.signal.aborted) return;
      if (res.ok) {
        await engine.setRender(res.image, hexRef.current);
        setCompare(false);
        setHint("hidden");
        say(nm.toastRendered);
      } else if (res.error === "not_configured") {
        say(nm.toastNotConfigured);
      } else if (res.error === "rate_limited") {
        say(nm.toastRateLimited);
      } else {
        say(nm.toastRenderFailed);
      }
    } finally {
      if (!ac.signal.aborted) setBusy(false);
    }
  }, [busy, code, say]);

  const openFile = useCallback(async (file: File | null | undefined) => {
    const engine = engineRef.current;
    if (!file || !engine || busy) return;
    if (!/^image\//.test(file.type) && !/\.(jpe?g|png|webp|heic|heif|avif)$/i.test(file.name || "")) {
      say(textRef.current.toastBadFile);
      return;
    }
    setCompare(false);
    if (!(await engine.load(file))) return;
    setHint("need");
    say(textRef.current.toastUploaded);
  }, [busy, say]);

  // вставка фото из буфера — пока примерка на странице
  useEffect(() => {
    const onPaste = (e: ClipboardEvent) => {
      const item = Array.from(e.clipboardData?.items ?? []).find((i) => i.type.startsWith("image/"));
      if (!item) return;
      e.preventDefault();
      void openFile(item.getAsFile());
      rootRef.current?.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "center" });
    };
    document.addEventListener("paste", onPaste);
    return () => document.removeEventListener("paste", onPaste);
  }, [openFile, reduce]);

  const toggleCompare = useCallback(() => {
    const engine = engineRef.current;
    if (!engine?.hasRender) return;
    setCompare((on) => { engine.showOriginal(!on); return !on; });
  }, []);

  const dragDepth = useRef(0);

  // подпись выбранного: название из подборки → код RAL → «свой цвет»
  const near = nearestRal(hex);
  const pickedName = code ? text.names[code] ?? `RAL ${code}` : text.customColor;
  const pickedCode = code ? `RAL ${code} · ${hex.toUpperCase()}` : `${hex.toUpperCase()} · ≈ RAL ${near.code}`;
  const ralList = ralQuery ? RAL_CLASSIC.filter(([c]) => c.startsWith(ralQuery)) : RAL_CLASSIC;
  const nf = new Intl.NumberFormat(NUMBER_LOCALE[locale], { maximumFractionDigits: 1 });
  const areaNum = Math.max(0, parseFloat(area.replace(",", ".")) || 0);
  const liters = (areaNum * coats) / COVERAGE_M2_PER_L;
  const cans = Math.ceil(liters / CAN_LITERS);
  const rule = new Intl.PluralRules(NUMBER_LOCALE[locale]).select(cans) as keyof PluralForms;
  const calcOut = areaNum > 0
    ? text.calcResult
      .replace("{liters}", nf.format(liters))
      .replace("{cans}", String(cans))
      .replace("{cansWord}", text.cans[rule] ?? text.cans.other)
      .replace("{can}", nf.format(CAN_LITERS))
    : text.calcEmpty;

  return (
    <div ref={rootRef}>
      <div className="stripes reveal" role="group" aria-label={text.stripesLabel}>
        {STRIPES.map((c) => {
          const s = swatchByCode(c);
          return (
            <button
              key={c}
              type="button"
              className={`stripe${c === code ? " on" : ""}`}
              style={{ "--c": s.hex, "--on": s.on ?? "#fff" } as CSSProperties}
              onClick={() => {
                pick(c);
                const r = rootRef.current?.querySelector(".vz")?.getBoundingClientRect();
                if (r && (r.top > innerHeight * 0.6 || r.bottom < 0)) rootRef.current?.querySelector(".vz")?.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "center" });
              }}
            >
              <span className="st-code"><bdi>RAL {c}</bdi></span>
              <span className="st-name">{text.names[c]}</span>
            </button>
          );
        })}
      </div>

      <div className={`vz${compare ? " cmp" : ""}`}>
        <div
          className="vz-stage card"
          ref={stageRef}
          onDragEnter={(e) => { e.preventDefault(); dragDepth.current++; setDragging(true); }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={() => { dragDepth.current = Math.max(0, dragDepth.current - 1); if (!dragDepth.current) setDragging(false); }}
          onDrop={(e) => { e.preventDefault(); dragDepth.current = 0; setDragging(false); void openFile(e.dataTransfer.files[0]); }}
        >
          <div className="vz-wrap" dir="ltr">
            <canvas className="vz-canvas" ref={canvasRef} width={584} height={784} role="img" aria-label={text.canvasAlt} />
            {hasRender && (
              <button
                type="button"
                className={`vz-compare${compare ? " on" : ""}`}
                aria-pressed={compare}
                onClick={toggleCompare}
              >
                <span className="tag">{compare ? text.before : text.after}</span>
              </button>
            )}
            <span className={`vz-hint${hint === "hidden" ? " hide" : ""}${hint === "need" ? " centered" : ""}`}>
              <i />{hint === "need" ? text.needRender : text.hint}
            </span>
          </div>
          <div className="vz-drop" hidden={!dragging}>{text.drop}</div>
          <div className="vz-busy" hidden={!busy}><div><div className="spin" /><span>{text.busy}</span></div></div>
        </div>

        <div className="vz-panel card">
          <ol className="steps-mini">
            {text.steps.map((s, i) => <li key={i}>{s}</li>)}
          </ol>
          <label className="btn btn-ghost upload">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3M12 4v12M7 9l5-5 5 5" />
            </svg>
            {text.upload}
            <input
              className="sr-only"
              type="file"
              accept="image/*"
              onChange={(e) => { void openFile(e.target.files?.[0]); e.target.value = ""; }}
            />
          </label>

          <div className="picked">
            <span className="big" style={{ "--c": hex } as CSSProperties} />
            <div>
              <b>{pickedName}</b>
              <code><bdi>{pickedCode}</bdi></code>
            </div>
          </div>

          <button type="button" className="btn btn-blue vz-render" onClick={() => void doRender()} disabled={busy}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
              <circle cx="12" cy="12" r="3.2" />
            </svg>
            {hasRender ? text.rerender : text.render}
          </button>
          <p className="small vz-render-hint">{text.renderHint}</p>
          <p className="privacy">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
            {text.privacy}
          </p>

          <div className="pal-tabs" role="tablist" aria-label={text.paletteLabel}>
            {(["set", "ral", "any"] as const).map((t) => (
              <button key={t} type="button" role="tab" aria-selected={tab === t} className="pal-tab" onClick={() => setTab(t)}>
                {text.tabs[t]}
              </button>
            ))}
          </div>

          {tab === "set" && (
            <div className="swatches" role="group" aria-label={text.paletteLabel}>
              {SWATCHES.map((s) => (
                <button
                  key={s.code}
                  type="button"
                  className="sw"
                  style={{ "--c": s.hex } as CSSProperties}
                  aria-pressed={s.code === code}
                  aria-label={`RAL ${s.code} ${text.names[s.code]}`}
                  onClick={() => pick(s.code)}
                />
              ))}
            </div>
          )}

          {tab === "ral" && (
            <div className="ral-all">
              <input
                className="ral-search"
                type="search"
                inputMode="numeric"
                dir="ltr"
                placeholder={text.ralSearch}
                aria-label={text.ralSearch}
                value={ralQuery}
                onChange={(e) => setRalQuery(e.target.value.replace(/\D/g, "").slice(0, 4))}
              />
              <div className="ral-grid" role="group" aria-label={text.tabs.ral}>
                {ralList.map(([c, h]) => (
                  <button
                    key={c}
                    type="button"
                    className="ral-sw"
                    style={{ "--c": h } as CSSProperties}
                    aria-pressed={c === code}
                    title={`RAL ${c}`}
                    onClick={() => pick(c)}
                  >
                    <bdi>{c}</bdi>
                  </button>
                ))}
                {ralList.length === 0 && <p className="small">{text.ralNone}</p>}
              </div>
            </div>
          )}

          {tab === "any" && (
            <div>
              <ColorPicker
                value={hex}
                onChange={(h) => pickAny(h)}
                labels={{ square: text.pickerSquare, hue: text.pickerHue, hex: "HEX" }}
              />
              {code === null && (
                <div className="near">
                  <span>{text.nearest}</span>
                  <button type="button" className="near-btn" onClick={() => pick(near.code)}>
                    <i style={{ background: near.hex }} />
                    <bdi>RAL {near.code}</bdi>
                  </button>
                </div>
              )}
              <p className="small">{text.pickerNote}</p>
            </div>
          )}

          <div className="actions actions-one">
            <button
              className="act"
              type="button"
              onClick={async () => {
                const engine = engineRef.current;
                if (!engine || busy) return;
                setCompare(false);
                if (await engine.load(DEMO_SRC)) { setHint("need"); say(text.toastDemo); }
              }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M3 11 12 4l9 7" /><path d="M5 10v10h14V10" />
              </svg>
              {text.demo}
            </button>
          </div>

          <div className="calc">
            <div className="calc-row">
              <div className="field">
                <label htmlFor="vz-area">{text.area}</label>
                <input id="vz-area" type="number" inputMode="decimal" min={1} max={500} step={0.5} value={area} onChange={(e) => setArea(e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="vz-coats">{text.coats}</label>
                <select id="vz-coats" value={coats} onChange={(e) => setCoats(Number(e.target.value))}>
                  {text.coatOptions.map((label, i) => <option key={i} value={i + 1}>{label}</option>)}
                </select>
              </div>
            </div>
            <div className="calc-out" aria-live="polite">{calcOut}</div>
            <p className="small">{text.calcNote}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
