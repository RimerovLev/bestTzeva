/**
 * Примерка цвета: человек сам красит стены валиком на своём фото. Фото не покидает браузер.
 *
 * Как краска ложится реалистично: цвет краски умножается на освещённость пикселя относительно
 * «светлой» части той же поверхности. Освещённость — размытая L* из Lab (тени, фактура, блики
 * сохраняются). Поверхности (стены) различаются по цветности и яркости исходника и смежности
 * мазков — поэтому тёмная синяя и белая стена в одном цвете выглядят одинаково, а свет и тень
 * одной стены остаются одной поверхностью. Края маски смягчены на 1px (среднее 3×3).
 *
 * Движок работает напрямую с холстами и DOM-элементами кольца/валика — на каждый мазок
 * React не перерисовывается.
 */

export type RollerMode = "paint" | "erase";

export interface RollerElements {
  canvas: HTMLCanvasElement;
  orig: HTMLCanvasElement;
  ring: HTMLElement;
  roller: HTMLElement;
  wrap: HTMLElement;
}

export interface RollerCallbacks {
  onBusy: (busy: boolean) => void;
  onUndoChange: (canUndo: boolean) => void;
  /** Человек начал красить — убрать подсказку, остановить демо. */
  onTouch: () => void;
  onError: () => void;
}

interface RegionMeta { a: number; b: number; y: number }
interface Prepared {
  tr: number; tg: number; tb: number; Yref: Float32Array; Yg: number;
  /** Множитель освещённости для закрашенных пикселей (0 — не посчитан, берётся Ys/Yref). */
  fac: Float32Array;
}

/** Сколько крупного перепада света (окно, угол комнаты) оставить на краске: 0 — стена ровная, 1 — как на фото. */
const BROAD_KEEP = 0.15;
/** Расширение готовой маски (демо) на тёмную синеватую кромку исходной стены, px. */
const MASK_EDGE_GROW = 3;

const MAX_SIDE = 1100;

const LIN = new Float32Array(256);
for (let i = 0; i < 256; i++) { const c = i / 255; LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
const GAM = new Uint8ClampedArray(4096);
for (let i = 0; i < 4096; i++) { const c = i / 4095; GAM[i] = Math.round(255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)); }
const fLab = (t: number) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
const fInv = (t: number) => { const t3 = t * t * t; return t3 > 0.008856 ? t3 : (t - 16 / 116) / 7.787; };
const gi = (v: number) => (v <= 0 ? 0 : v >= 1 ? 4095 : (v * 4095) | 0);
const nextFrame = () => new Promise<void>((r) => requestAnimationFrame(() => setTimeout(r, 0)));

async function decodeBlob(blob: Blob): Promise<ImageBitmap | HTMLImageElement> {
  // EXIF-поворот: фото с телефона иначе ляжет боком
  try { return await createImageBitmap(blob, { imageOrientation: "from-image" }); } catch { /* старый Safari */ }
  try { return await createImageBitmap(blob); } catch { /* дальше — через <img> */ }
  const url = URL.createObjectURL(blob);
  try { const img = new Image(); img.src = url; await img.decode(); return img; } finally { setTimeout(() => URL.revokeObjectURL(url), 2000); }
}
async function loadUrl(src: string): Promise<HTMLImageElement> {
  const img = new Image();
  img.src = src;
  await img.decode();
  return img;
}

export class RollerEngine {
  private el: RollerElements;
  private cb: RollerCallbacks;
  private ctx: CanvasRenderingContext2D;
  private octx: CanvasRenderingContext2D;
  private off = document.createElement("canvas");
  private offx: CanvasRenderingContext2D;

  private W = 0; private H = 0; private N = 0;
  private origPx: Uint8ClampedArray | null = null;
  private Ys: Float32Array = new Float32Array(0); private CA: Float32Array = new Float32Array(0); private CB: Float32Array = new Float32Array(0);
  private rid = new Uint8Array(0);
  private regMeta: (RegionMeta | null)[] = [null];
  private regCount = 0;
  private mask = new Uint8Array(0);
  private alpha = new Float32Array(0);
  private result: ImageData | null = null;
  private P: Prepared | null = null;
  private undoStack: { m: Uint8Array; r: Uint8Array }[] = [];
  private dirty: { x0: number; y0: number; x1: number; y1: number } | null = null;
  private painting = false;
  private last: { x: number; y: number } | null = null;
  private sweepRaf = 0;

  color = "#2271b3";
  mode: RollerMode = "paint";
  brushPx = 28;
  compare = false;
  busy = false;
  reduce = false;

  constructor(el: RollerElements, cb: RollerCallbacks) {
    this.el = el;
    this.cb = cb;
    const ctx = el.canvas.getContext("2d", { willReadFrequently: true });
    const octx = el.orig.getContext("2d");
    const offx = this.off.getContext("2d");
    if (!ctx || !octx || !offx) throw new Error("canvas 2d unavailable");
    this.ctx = ctx; this.octx = octx; this.offx = offx;
    this.bind();
  }

  get loaded() { return this.W > 0; }

  // ── подготовка ───────────────────────────────────────────────────────────

  private boxBlur(src: Float32Array, r: number): Float32Array {
    const { W, H, N } = this;
    const tmp = new Float32Array(N), out = new Float32Array(N), win = 2 * r + 1;
    for (let y = 0; y < H; y++) {
      let s = 0; const row = y * W;
      for (let x = -r; x <= r; x++) s += src[row + Math.min(W - 1, Math.max(0, x))]!;
      for (let x = 0; x < W; x++) { tmp[row + x] = s / win; s += src[row + Math.min(W - 1, x + r + 1)]! - src[row + Math.max(0, x - r)]!; }
    }
    for (let x = 0; x < W; x++) {
      let s = 0;
      for (let y = -r; y <= r; y++) s += tmp[Math.min(H - 1, Math.max(0, y)) * W + x]!;
      for (let y = 0; y < H; y++) { out[y * W + x] = s / win; s += tmp[Math.min(H - 1, y + r + 1) * W + x]! - tmp[Math.max(0, y - r) * W + x]!; }
    }
    return out;
  }

  /** Освещённость (Y) и цветность (a*, b*) каждого пикселя. */
  private prepLight() {
    const { N } = this, o = this.origPx!;
    const L = new Float32Array(N), A = new Float32Array(N), B = new Float32Array(N);
    for (let i = 0, p = 0; i < N; i++, p += 4) {
      const R = LIN[o[p]!]!, G = LIN[o[p + 1]!]!, Bl = LIN[o[p + 2]!]!;
      const fx = fLab((0.4124 * R + 0.3576 * G + 0.1805 * Bl) / 0.95047), fy = fLab(0.2126 * R + 0.7152 * G + 0.0722 * Bl), fz = fLab((0.0193 * R + 0.1192 * G + 0.9505 * Bl) / 1.08883);
      L[i] = 116 * fy - 16; A[i] = 500 * (fx - fy); B[i] = 200 * (fy - fz);
    }
    const Lb = this.boxBlur(L, 1);
    this.CA = this.boxBlur(A, 2);
    this.CB = this.boxBlur(B, 2);
    this.Ys = new Float32Array(N);
    for (let i = 0; i < N; i++) this.Ys[i] = fInv((Lb[i]! + 16) / 116);
  }

  /** Опорная яркость каждой поверхности — 85-й перцентиль Y закрашенных пикселей. */
  private prepare(hex: string) {
    const tr = LIN[parseInt(hex.slice(1, 3), 16)] ?? 0, tg = LIN[parseInt(hex.slice(3, 5), 16)] ?? 0, tb = LIN[parseInt(hex.slice(5, 7), 16)] ?? 0;
    const R = this.regCount + 1, hist = new Uint32Array(R * 256), tot = new Uint32Array(R), gh = new Uint32Array(256);
    let gt = 0;
    for (let i = 0; i < this.N; i++) if (this.mask[i]) { const b = Math.min(255, (this.Ys[i]! * 255) | 0), r = this.rid[i]!; const k = r * 256 + b; hist[k] = hist[k]! + 1; tot[r] = tot[r]! + 1; gh[b] = gh[b]! + 1; gt++; }
    const pct = (h: Uint32Array, o: number, total: number) => {
      if (!total) return 0;
      const lim = total * 0.85; let c = 0;
      for (let b = 0; b < 256; b++) { c += h[o + b]!; if (c >= lim) return (b + 0.5) / 255; }
      return 1;
    };
    const Yref = new Float32Array(R);
    for (let r = 1; r < R; r++) Yref[r] = pct(hist, r * 256, tot[r]!);
    const Yg = pct(gh, 0, gt) || 0.5;
    this.P = { tr, tg, tb, Yref, Yg, fac: this.lightFactor(Yref, Yg) };
  }

  /**
   * Освещённость краски без крупного перепада света. На фото стена у окна может быть в разы темнее,
   * чем у противоположного края, — перенесённый «как есть», этот перепад делает новый цвет двухцветным.
   * Считаем крупномасштабный свет внутри закрашенного (нормированное размытие Ys по маске, радиус ~1/12
   * кадра) и оставляем от него BROAD_KEEP; фактура и мелкие тени (Ys / размытое) сохраняются целиком.
   */
  private lightFactor(Yref: Float32Array, Yg: number): Float32Array {
    const { N, W, H, mask, rid, Ys } = this;
    let num: Float32Array = new Float32Array(N), den: Float32Array = new Float32Array(N);
    for (let i = 0; i < N; i++) if (mask[i]) { num[i] = Ys[i]!; den[i] = 1; }
    const r = Math.max(4, Math.round(Math.max(W, H) / 12));
    for (let k = 0; k < 2; k++) { num = this.boxBlur(num, r); den = this.boxBlur(den, r); }
    const fac = new Float32Array(N);
    for (let i = 0; i < N; i++) {
      if (!mask[i] || den[i]! < 1e-4) continue;
      const broad = num[i]! / den[i]!, ref = Yref[rid[i]!] || Yg;
      if (broad > 1e-5) fac[i] = (Ys[i]! / broad) * Math.pow(broad / ref, BROAD_KEEP);
    }
    return fac;
  }

  /** Расширить маску на тёмную синеватую кромку (смешанные с карнизом пиксели исходной стены). */
  private growMaskEdge(n: number) {
    const { W, H, N, Ys, CB } = this;
    for (let k = 0; k < n; k++) {
      const add: number[] = [];
      for (let i = 0; i < N; i++) {
        if (this.mask[i] || Ys[i]! >= 0.35 || CB[i]! >= -1) continue;
        const x = i % W, y = (i / W) | 0;
        if ((x > 0 && this.mask[i - 1]) || (x < W - 1 && this.mask[i + 1]) || (y > 0 && this.mask[i - W]) || (y < H - 1 && this.mask[i + W])) add.push(i);
      }
      for (const i of add) this.mask[i] = 1;
    }
  }

  /** Новый мазок присоединяется к соседней поверхности того же цвета или становится новой. */
  private assignGroup(list: number[]) {
    if (!list.length) return;
    const { W, H, rid, CA, CB, Ys } = this;
    let sa = 0, sb = 0, sy = 0; const nb = new Set<number>();
    for (const i of list) {
      sa += CA[i]!; sb += CB[i]!; sy += Ys[i]!;
      const x = i % W, y = (i / W) | 0;
      for (const j of [x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1]) if (j >= 0 && rid[j]) nb.add(rid[j]);
    }
    const n = list.length, ma = sa / n, mb = sb / n, my = sy / n;
    let id = 0, best = 9;
    for (const r of nb) {
      const m = this.regMeta[r];
      if (!m) continue;
      const d = Math.hypot(m.a - ma, m.b - mb), ratio = Math.max(m.y, my) / Math.max(1e-3, Math.min(m.y, my));
      // цветная поверхность (тот же оттенок) — объединяем даже свет с тенью; нейтральные — ещё и по яркости
      const sameHue = d < 6 && Math.hypot(m.a, m.b) > 4, sameNeutral = d < 9 && ratio < 4;
      if (d < best && (sameHue || sameNeutral)) { best = d; id = r; }
    }
    if (!id) { id = Math.min(250, ++this.regCount); this.regMeta[id] = { a: ma, b: mb, y: my }; }
    for (const i of list) rid[i] = id;
  }
  /** Мазок, захвативший и свет, и глубокую тень (p90/p10 > 8), делится на две поверхности. */
  private assignNew() {
    const idx: number[] = [];
    for (let i = 0; i < this.N; i++) if (this.mask[i] && !this.rid[i]) idx.push(i);
    if (!idx.length) return;
    const ys = idx.map((i) => this.Ys[i]!).sort((a, b) => a - b);
    const p10 = Math.max(0.004, ys[Math.floor(ys.length * 0.1)]!), p90 = ys[Math.floor(ys.length * 0.9)]!;
    if (p90 > p10 * 8) {
      const th = Math.sqrt(p10 * p90);
      this.assignGroup(idx.filter((i) => this.Ys[i]! < th));
      this.assignGroup(idx.filter((i) => this.Ys[i]! >= th));
    } else this.assignGroup(idx);
  }

  private updateAlpha(x0: number, y0: number, x1: number, y1: number) {
    const { W, H, mask, alpha } = this;
    x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(W - 1, x1); y1 = Math.min(H - 1, y1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      let s = 0, c = 0;
      for (let dy = -1; dy <= 1; dy++) {
        const yy = y + dy; if (yy < 0 || yy >= H) continue;
        for (let dx = -1; dx <= 1; dx++) { const xx = x + dx; if (xx < 0 || xx >= W) continue; s += mask[yy * W + xx]!; c++; }
      }
      alpha[y * W + x] = s / c;
    }
  }
  private compose(x0: number, y0: number, x1: number, y1: number) {
    const { W, H, alpha, rid, Ys } = this, o = this.origPx!, d = this.result!.data, { tr, tg, tb, Yref, Yg, fac } = this.P!;
    x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(W - 1, x1); y1 = Math.min(H - 1, y1);
    for (let y = y0; y <= y1; y++) {
      let i = y * W + x0, p = i * 4;
      for (let x = x0; x <= x1; x++, i++, p += 4) {
        const al = alpha[i]!;
        if (al <= 0) { d[p] = o[p]!; d[p + 1] = o[p + 1]!; d[p + 2] = o[p + 2]!; d[p + 3] = 255; continue; }
        // посчитан в prepare — без крупного перепада света; свежий мазок до отпускания — по-старому
        let f = fac[i] || Ys[i]! / (Yref[rid[i]!] || Yg);
        // тени смягчаем вдвое, блики ограничиваем — иначе тёмная часть стены уходит в чёрный
        f = f < 1 ? 0.5 + 0.5 * f : Math.min(1.12, 1 + 0.5 * (f - 1));
        const r = GAM[gi(tr * f)]!, g = GAM[gi(tg * f)]!, b = GAM[gi(tb * f)]!;
        const o0 = o[p]!, o1 = o[p + 1]!, o2 = o[p + 2]!;
        d[p] = o0 + (r - o0) * al; d[p + 1] = o1 + (g - o1) * al; d[p + 2] = o2 + (b - o2) * al; d[p + 3] = 255;
      }
    }
  }
  private fullRender() {
    this.prepare(this.color);
    this.compose(0, 0, this.W - 1, this.H - 1);
    this.offx.putImageData(this.result!, 0, 0);
  }
  private show() { this.ctx.drawImage(this.off, 0, 0); }

  anyMask(): boolean {
    for (let i = 0; i < this.N; i += 3) if (this.mask[i]) return true;
    return false;
  }

  // ── загрузка ─────────────────────────────────────────────────────────────

  async load(src: Blob | string, maskSrc?: string): Promise<boolean> {
    this.busy = true;
    this.cb.onBusy(true);
    try {
      const bmp = src instanceof Blob ? await decodeBlob(src) : await loadUrl(src);
      const bw = "naturalWidth" in bmp ? bmp.naturalWidth : bmp.width, bh = "naturalHeight" in bmp ? bmp.naturalHeight : bmp.height;
      if (!bw || !bh) throw new Error("empty image");
      const s = Math.min(1, MAX_SIDE / Math.max(bw, bh));
      const W = Math.max(1, Math.round(bw * s)), H = Math.max(1, Math.round(bh * s));
      this.W = W; this.H = H; this.N = W * H;
      const { canvas, orig } = this.el;
      canvas.width = orig.width = this.off.width = W;
      canvas.height = orig.height = this.off.height = H;
      this.ctx.drawImage(bmp, 0, 0, W, H);
      this.octx.drawImage(bmp, 0, 0, W, H);
      this.origPx = this.ctx.getImageData(0, 0, W, H).data.slice();
      await nextFrame();
      this.prepLight();
      this.rid = new Uint8Array(this.N); this.regMeta = [null]; this.regCount = 0;
      this.mask = new Uint8Array(this.N); this.alpha = new Float32Array(this.N); this.result = this.ctx.createImageData(W, H);
      this.undoStack = []; this.cb.onUndoChange(false);
      if (maskSrc) {
        const m = await loadUrl(maskSrc), t = document.createElement("canvas");
        t.width = W; t.height = H;
        const tx = t.getContext("2d", { willReadFrequently: true });
        if (tx) {
          tx.drawImage(m, 0, 0, W, H);
          const md = tx.getImageData(0, 0, W, H).data;
          for (let i = 0, p = 0; i < this.N; i++, p += 4) this.mask[i] = md[p]! > 127 ? 1 : 0;
          this.growMaskEdge(MASK_EDGE_GROW);
          this.assignNew();
          this.updateAlpha(0, 0, W - 1, H - 1);
        }
      }
      this.fullRender();
      this.show();
      return true;
    } catch {
      this.cb.onError();
      return false;
    } finally {
      this.busy = false;
      this.cb.onBusy(false);
    }
  }

  // ── действия ─────────────────────────────────────────────────────────────

  private pushUndo() {
    this.undoStack.push({ m: this.mask.slice(), r: this.rid.slice() });
    if (this.undoStack.length > 20) this.undoStack.shift();
    this.cb.onUndoChange(true);
  }
  undo() {
    const u = this.undoStack.pop();
    if (!u || this.busy) return;
    this.mask.set(u.m); this.rid.set(u.r);
    this.cb.onUndoChange(this.undoStack.length > 0);
    this.updateAlpha(0, 0, this.W - 1, this.H - 1);
    this.fullRender(); this.show();
  }
  /** false — нечего очищать. */
  clear(): boolean {
    if (!this.loaded || this.busy || !this.anyMask()) return false;
    this.pushUndo();
    this.mask.fill(0); this.rid.fill(0); this.alpha.fill(0);
    this.fullRender(); this.show();
    return true;
  }

  /**
   * Новый цвет: всё закрашенное перекрашивается сразу, по картинке проезжает валик.
   * Возвращает false, если перекрашивать нечего.
   */
  setColor(hex: string): boolean {
    const changed = hex !== this.color;
    this.color = hex;
    this.el.roller.style.setProperty("--c", hex);
    if (!this.loaded || !this.anyMask()) return false;
    if (changed) this.sweep();
    return true;
  }
  private sweep() {
    cancelAnimationFrame(this.sweepRaf);
    this.fullRender();
    if (this.reduce) { this.show(); return; }
    const { roller } = this.el, { W, H } = this;
    roller.classList.add("on");
    const t0 = performance.now(), dur = 800;
    const st = (now: number) => {
      const k = Math.min(1, (now - t0) / dur), e = k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2, x = Math.max(1, Math.round(e * W));
      this.ctx.drawImage(this.off, 0, 0, x, H, 0, 0, x, H);
      roller.style.left = `${(e * 100).toFixed(2)}%`;
      if (k < 1) this.sweepRaf = requestAnimationFrame(st);
      else { this.show(); roller.classList.remove("on"); }
    };
    this.sweepRaf = requestAnimationFrame(st);
  }

  setMode(mode: RollerMode) { this.mode = mode; this.sizeRing(); }
  setBrush(px: number) { this.brushPx = px; this.sizeRing(); }
  setCompare(on: boolean) { this.compare = on; this.el.ring.classList.remove("on"); }

  // ── валик и ластик ───────────────────────────────────────────────────────

  private toCanvas(e: PointerEvent) {
    const r = this.el.canvas.getBoundingClientRect();
    return { x: (e.clientX - r.left) * this.W / r.width, y: (e.clientY - r.top) * this.H / r.height, s: this.W / r.width };
  }
  private stamp(x: number, y: number, rad: number, val: 0 | 1) {
    const { W, H, mask, rid } = this, r2 = rad * rad;
    const x0 = Math.max(0, Math.floor(x - rad)), x1 = Math.min(W - 1, Math.ceil(x + rad)), y0 = Math.max(0, Math.floor(y - rad)), y1 = Math.min(H - 1, Math.ceil(y + rad));
    for (let yy = y0; yy <= y1; yy++) {
      const dy = yy - y, row = yy * W;
      for (let xx = x0; xx <= x1; xx++) { const dx = xx - x; if (dx * dx + dy * dy <= r2) { const i = row + xx; mask[i] = val; if (!val) rid[i] = 0; } }
    }
    const d = this.dirty;
    if (!d) this.dirty = { x0, y0, x1, y1 };
    else { if (x0 < d.x0) d.x0 = x0; if (y0 < d.y0) d.y0 = y0; if (x1 > d.x1) d.x1 = x1; if (y1 > d.y1) d.y1 = y1; }
  }
  private flush() {
    if (!this.dirty) return;
    const { x0, y0, x1, y1 } = this.dirty; this.dirty = null;
    this.updateAlpha(x0 - 1, y0 - 1, x1 + 1, y1 + 1);
    this.compose(x0 - 1, y0 - 1, x1 + 1, y1 + 1);
    const bx = Math.max(0, x0 - 1), by = Math.max(0, y0 - 1);
    this.ctx.putImageData(this.result!, 0, 0, bx, by, Math.min(this.W, x1 + 2) - bx, Math.min(this.H, y1 + 2) - by);
  }
  private placeRing(e: PointerEvent) {
    const r = this.el.wrap.getBoundingClientRect();
    this.el.ring.style.left = `${e.clientX - r.left}px`;
    this.el.ring.style.top = `${e.clientY - r.top}px`;
  }
  private sizeRing() {
    const { ring } = this.el;
    ring.style.width = ring.style.height = `${this.brushPx}px`;
    ring.classList.toggle("erase", this.mode === "erase");
  }

  private onEnter = (e: PointerEvent) => { if (e.pointerType === "mouse") { this.placeRing(e); this.el.ring.classList.add("on"); } };
  private onLeave = () => { if (!this.painting) this.el.ring.classList.remove("on"); };
  private onDown = (e: PointerEvent) => {
    if (this.compare || this.busy || !this.loaded) return;
    e.preventDefault();
    this.cb.onTouch();
    if (!this.P) this.prepare(this.color);
    const { x, y, s } = this.toCanvas(e);
    this.pushUndo();
    this.painting = true;
    this.last = { x, y };
    try { this.el.canvas.setPointerCapture(e.pointerId); } catch { /* ignore */ }
    this.placeRing(e);
    this.el.ring.classList.add("on");
    this.stamp(x, y, (this.brushPx / 2) * s, this.mode === "paint" ? 1 : 0);
    this.flush();
  };
  private onMove = (e: PointerEvent) => {
    if (e.pointerType === "mouse" || this.painting) this.placeRing(e);
    if (!this.painting || !this.last) return;
    const { x, y, s } = this.toCanvas(e), rad = (this.brushPx / 2) * s, val = this.mode === "paint" ? 1 : 0;
    const dist = Math.hypot(x - this.last.x, y - this.last.y), n = Math.max(1, Math.ceil(dist / Math.max(1, rad / 3)));
    for (let k = 1; k <= n; k++) this.stamp(this.last.x + (x - this.last.x) * k / n, this.last.y + (y - this.last.y) * k / n, rad, val);
    this.last = { x, y };
    this.flush();
  };
  private onUp = (e: PointerEvent) => {
    if (!this.painting) return;
    this.painting = false;
    if (e.pointerType !== "mouse") this.el.ring.classList.remove("on");
    this.assignNew();
    this.fullRender();
    this.show();
  };

  private bind() {
    const c = this.el.canvas;
    c.addEventListener("pointerenter", this.onEnter);
    c.addEventListener("pointerleave", this.onLeave);
    c.addEventListener("pointerdown", this.onDown);
    c.addEventListener("pointermove", this.onMove);
    c.addEventListener("pointerup", this.onUp);
    c.addEventListener("pointercancel", this.onUp);
    this.sizeRing();
  }
  dispose() {
    cancelAnimationFrame(this.sweepRaf);
    const c = this.el.canvas;
    c.removeEventListener("pointerenter", this.onEnter);
    c.removeEventListener("pointerleave", this.onLeave);
    c.removeEventListener("pointerdown", this.onDown);
    c.removeEventListener("pointermove", this.onMove);
    c.removeEventListener("pointerup", this.onUp);
    c.removeEventListener("pointercancel", this.onUp);
  }
}
