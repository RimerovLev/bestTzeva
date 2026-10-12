"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import type { Locale } from "@/lib/i18n/config";
import { RollerEngine, type RollerMode } from "@/lib/motion/roller";
import { sweepHint } from "@/lib/motion/sweep";
import {
  CAN_LITERS, COVERAGE_M2_PER_L, DEFAULT_SWATCH, STRIPES, SWATCHES, swatchByCode,
} from "@/lib/motion/palette";
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
  compareRange: string;
  hint: string;
  drop: string;
  busy: string;
  steps: string[];
  upload: string;
  privacy: string;
  tool: string;
  roller: string;
  eraser: string;
  size: string;
  sizes: string[];
  helpPaint: string;
  helpErase: string;
  paletteLabel: string;
  tabs: { set: string; ral: string; any: string };
  ralSearch: string;
  ralNone: string;
  customColor: string;
  nearest: string;
  pickerSquare: string;
  pickerHue: string;
  pickerNote: string;
  compare: string;
  undo: string;
  clear: string;
  demo: string;
  area: string;
  coats: string;
  coatOptions: string[];
  calcResult: string;
  cans: PluralForms;
  calcEmpty: string;
  calcNote: string;
  toastPicked: string;
  toastNothingToClear: string;
  toastCleared: string;
  toastDemo: string;
  toastUploaded: string;
  toastBadFile: string;
  toastOpenError: string;
}

const SIZES = [6, 14, 28, 52, 90];
const SIZE_DOTS = [4, 8, 13, 19, 26];
const NUMBER_LOCALE: Record<Locale, string> = { he: "he-IL", ru: "ru-RU" };
/** Пример: та же гостиная с белыми стенами — красит сам человек. */
const DEMO_SRC = "/assets/motion/demo-white.webp";

/**
 * Примерка цвета: полосы-оттенки, холст с валиком и панель. Фото обрабатывается только в браузере.
 * Состояние инструментов — в React, сами мазки — в RollerEngine (без перерисовки React на каждое
 * движение). Холст и шторка сравнения — в LTR: координаты картинки не зависят от языка.
 */
export function ColorStudio({ locale, text }: { locale: Locale; text: ColorStudioText }) {
  const { say, reduce } = useMotion();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const origRef = useRef<HTMLCanvasElement>(null);
  const ringRef = useRef<HTMLDivElement>(null);
  const rollerRef = useRef<HTMLDivElement>(null);
  const wrapRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<RollerEngine | null>(null);
  const userTouched = useRef(false);
  const cmpTouched = useRef(false);

  /** Выбранный цвет: HEX и код RAL (null — произвольный цвет из пикера). */
  const [hex, setHex] = useState(() => swatchByCode(DEFAULT_SWATCH).hex);
  const [code, setCode] = useState<string | null>(DEFAULT_SWATCH);
  const [tab, setTab] = useState<"set" | "ral" | "any">("set");
  const [ralQuery, setRalQuery] = useState("");
  const [mode, setMode] = useState<RollerMode>("paint");
  const [size, setSize] = useState(28);
  const [busy, setBusy] = useState(false);
  const [canUndo, setCanUndo] = useState(false);
  const [compare, setCompare] = useState(false);
  const [cmpPos, setCmpPos] = useState(50);
  const [hint, setHint] = useState<"demo" | "upload" | "hidden">("demo");
  const [dragging, setDragging] = useState(false);
  const [area, setArea] = useState("14");
  const [coats, setCoats] = useState(2);

  const textRef = useRef(text);
  textRef.current = text;

  const touch = useCallback(() => {
    userTouched.current = true;
    setHint((h) => (h === "hidden" ? h : "hidden"));
  }, []);

  // движок + демо-фото (белые стены, ничего не закрашено)
  useEffect(() => {
    const canvas = canvasRef.current, orig = origRef.current, ring = ringRef.current, roller = rollerRef.current, wrap = wrapRef.current;
    if (!canvas || !orig || !ring || !roller || !wrap) return;
    const engine = new RollerEngine({ canvas, orig, ring, roller, wrap }, {
      onBusy: setBusy,
      onUndoChange: setCanUndo,
      onTouch: () => { userTouched.current = true; setHint("hidden"); },
      onError: () => say(textRef.current.toastOpenError),
    });
    engine.color = swatchByCode(DEFAULT_SWATCH).hex;
    roller.style.setProperty("--c", engine.color);
    engineRef.current = engine;
    void engine.load(DEMO_SRC);
    return () => { engine.dispose(); engineRef.current = null; };
  }, [say]);

  useEffect(() => { if (engineRef.current) engineRef.current.reduce = reduce; }, [reduce]);
  useEffect(() => { engineRef.current?.setMode(mode); }, [mode]);
  useEffect(() => { engineRef.current?.setBrush(size); }, [size]);

  /** Новый цвет: перекрасить закрашенное (валик проезжает) или подсказать, что пора красить. */
  const applyHex = useCallback((nextHex: string, nextCode: string | null, quiet = false) => {
    setHex(nextHex);
    setCode(nextCode);
    const engine = engineRef.current;
    if (!engine) return;
    const painted = engine.setColor(nextHex);
    if (!painted && !quiet && engine.loaded) say(textRef.current.toastPicked);
  }, [say]);
  const pick = useCallback((nextCode: string) => applyHex(ralHex(nextCode) ?? swatchByCode(nextCode).hex, nextCode), [applyHex]);

  // пикер шлёт цвет на каждое движение: образец обновляем сразу, а стену перекрашиваем, когда рука
  // остановилась (иначе валик проезжал бы на каждый пиксель перетаскивания); подсказка — один раз
  const pickerToastShown = useRef(false);
  const pickerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pickAny = useCallback((nextHex: string) => {
    setHex(nextHex);
    setCode(null);
    if (pickerTimer.current) clearTimeout(pickerTimer.current);
    pickerTimer.current = setTimeout(() => {
      applyHex(nextHex, null, pickerToastShown.current);
      pickerToastShown.current = true;
    }, 180);
  }, [applyHex]);
  useEffect(() => () => { if (pickerTimer.current) clearTimeout(pickerTimer.current); }, []);

  const setCmp = useCallback((on: boolean) => {
    setCompare(on);
    engineRef.current?.setCompare(on);
    if (!on) return;
    touch();
    cmpTouched.current = false;
    setCmpPos(50);
    if (!reduce) sweepHint(setCmpPos, 88, 50, 1100, () => cmpTouched.current);
  }, [reduce, touch]);

  const openFile = useCallback(async (file: File | null | undefined) => {
    const engine = engineRef.current;
    if (!file || !engine || engine.busy) return;
    if (!/^image\//.test(file.type) && !/\.(jpe?g|png|webp|heic|heif|avif)$/i.test(file.name || "")) {
      say(textRef.current.toastBadFile);
      return;
    }
    setCmp(false);
    touch();
    if (!(await engine.load(file))) return;
    setHint("upload");
    setMode("paint");
    say(textRef.current.toastUploaded);
  }, [say, setCmp, touch]);

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
                touch();
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

      <div className={`vz${compare ? " cmp" : ""}`} data-mode={mode}>
        <div
          className="vz-stage card"
          ref={stageRef}
          onDragEnter={(e) => { e.preventDefault(); dragDepth.current++; setDragging(true); }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={() => { dragDepth.current = Math.max(0, dragDepth.current - 1); if (!dragDepth.current) setDragging(false); }}
          onDrop={(e) => { e.preventDefault(); dragDepth.current = 0; setDragging(false); void openFile(e.dataTransfer.files[0]); }}
        >
          <div className="vz-wrap" ref={wrapRef} dir="ltr" style={{ "--pos": `${cmpPos}%` } as CSSProperties}>
            <canvas className="vz-canvas" ref={canvasRef} width={584} height={784} role="img" aria-label={text.canvasAlt} />
            <canvas className="vz-orig" ref={origRef} aria-hidden />
            <div className="vz-roller" ref={rollerRef} aria-hidden />
            <div className="vz-ring" ref={ringRef} aria-hidden />
            <div className="vz-cmp" style={{ "--pos": `${cmpPos}%` } as CSSProperties}>
              <div className="handle" />
              <span className="tag tag-before">{text.before}</span>
              <span className="tag tag-after">{text.after}</span>
              <input
                type="range"
                min={0}
                max={100}
                step={0.1}
                value={cmpPos}
                aria-label={text.compareRange}
                onChange={(e) => { cmpTouched.current = true; setCmpPos(Number(e.target.value)); }}
              />
            </div>
            <span className={`vz-hint${hint === "hidden" ? " hide" : ""}${hint === "upload" ? " centered" : ""}`}><i />{text.hint}</span>
          </div>
          <div className="vz-drop" hidden={!dragging}>{text.drop}</div>
          <div className="vz-busy" hidden={!busy}><div><div className="spin" /><span>{text.busy}</span></div></div>
        </div>

        <div className="vz-panel card">
          <ol className="steps-mini">
            {text.steps.map((s, i) => <li key={i}>{s}</li>)}
          </ol>
          <label className="btn btn-blue upload">
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
          <p className="privacy">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
            {text.privacy}
          </p>

          <div>
            <p className="lbl" id="vz-mode-lbl">{text.tool}</p>
            <div className="seg" role="group" aria-labelledby="vz-mode-lbl">
              <button type="button" aria-pressed={mode === "paint"} onClick={() => { setMode("paint"); if (compare) setCmp(false); }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                  <rect x="3" y="3" width="15" height="6" rx="2" /><path d="M18 6h2a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-8v3" /><rect x="10" y="14" width="4" height="7" rx="1" />
                </svg>
                {text.roller}
              </button>
              <button type="button" aria-pressed={mode === "erase"} onClick={() => { setMode("erase"); if (compare) setCmp(false); }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                  <path d="m7 21-4-4L14 6l7 7-8 8H7Z" /><path d="M11 9l7 7" />
                </svg>
                {text.eraser}
              </button>
            </div>
          </div>

          <div>
            <p className="lbl" id="vz-size-lbl">{text.size}</p>
            <div className="sizes" role="group" aria-labelledby="vz-size-lbl">
              {SIZES.map((px, i) => (
                <button key={px} type="button" className="size" aria-pressed={size === px} aria-label={text.sizes[i]} onClick={() => setSize(px)}>
                  <i style={{ "--d": `${SIZE_DOTS[i] ?? 13}px` } as CSSProperties} />
                </button>
              ))}
            </div>
            <p className="small" style={{ marginTop: 10 }} aria-live="polite">{mode === "paint" ? text.helpPaint : text.helpErase}</p>
          </div>

          <div className="picked">
            <span className="big" style={{ "--c": hex } as CSSProperties} />
            <div>
              <b>{pickedName}</b>
              <code><bdi>{pickedCode}</bdi></code>
            </div>
          </div>

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
                  onClick={() => { touch(); if (mode !== "paint") setMode("paint"); pick(s.code); }}
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
                    onClick={() => { touch(); if (mode !== "paint") setMode("paint"); pick(c); }}
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
                onChange={(h) => { touch(); if (mode !== "paint") setMode("paint"); pickAny(h); }}
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

          <div className="actions">
            <button className="act" type="button" aria-pressed={compare} onClick={() => setCmp(!compare)}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden>
                <rect x="3" y="4" width="18" height="16" rx="2" /><path d="M12 4v16" />
              </svg>
              {text.compare}
            </button>
            <button className="act" type="button" disabled={!canUndo} onClick={() => engineRef.current?.undo()}>
              <svg className="icon-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M9 14 4 9l5-5" /><path d="M4 9h11a5 5 0 0 1 0 10h-3" />
              </svg>
              {text.undo}
            </button>
            <button
              className="act"
              type="button"
              onClick={() => {
                const engine = engineRef.current;
                if (!engine?.loaded || engine.busy) return;
                say(engine.clear() ? text.toastCleared : text.toastNothingToClear);
              }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" />
              </svg>
              {text.clear}
            </button>
            <button
              className="act"
              type="button"
              onClick={async () => {
                const engine = engineRef.current;
                if (!engine || engine.busy) return;
                setCmp(false);
                touch();
                if (await engine.load(DEMO_SRC)) say(text.toastDemo);
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
