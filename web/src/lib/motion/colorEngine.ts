/**
 * Движок «примерки цвета» на ИИ. Один платный вызов OpenAI на комнату: фото уходит на бэкенд,
 * возвращается фотореалистичный рендер со стенами выбранного цвета. Дальше смена цвета — локальная:
 * стены находятся по близости оттенка к цвету базового рендера, и перекрашиваются в браузере с
 * сохранением светотени (пропорциональный сдвиг в HSV). Это бесплатно и мгновенно, без новых вызовов.
 *
 * Координаты картинки от языка не зависят; движок ничего не знает про локаль.
 */

export type EngineHooks = {
  onBusy?: (busy: boolean) => void;
  onHasRender?: (has: boolean) => void;
  onError?: () => void;
};

/** Загрузка картинки из File или URL в HTMLImageElement. */
function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("image load failed"));
    img.src = src;
  });
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

/** RGB(0..255) → HSV(0..1). */
function rgbToHsv(r: number, g: number, b: number): [number, number, number] {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h /= 6;
    if (h < 0) h += 1;
  }
  const s = max === 0 ? 0 : d / max;
  return [h, s, max];
}

/** HSV(0..1) → RGB(0..255), запись в out по смещению i. */
function hsvToRgb(h: number, s: number, v: number, out: Uint8ClampedArray, i: number): void {
  const idx = Math.floor(h * 6) % 6;
  const f = h * 6 - Math.floor(h * 6);
  const p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
  let r = 0, g = 0, b = 0;
  switch (idx) {
    case 0: r = v; g = t; b = p; break;
    case 1: r = q; g = v; b = p; break;
    case 2: r = p; g = v; b = t; break;
    case 3: r = p; g = q; b = v; break;
    case 4: r = t; g = p; b = v; break;
    default: r = v; g = p; b = q; break;
  }
  out[i] = r * 255; out[i + 1] = g * 255; out[i + 2] = b * 255;
}

/** Порог близости оттенка к цвету стены (доля кольца 0..1) и минимальная насыщенность стены. */
const HUE_BAND = 0.11;
const SAT_FLOOR = 0.18;

export class ColorEngine {
  private display: HTMLCanvasElement;
  private dctx: CanvasRenderingContext2D;
  private hooks: EngineHooks;

  /** Оригинальное фото (для режима «показать оригинал»). */
  private origImage: HTMLImageElement | null = null;
  /** Пиксели базового рендера ИИ (без изменений), на их размер настроен холст. */
  private baseRgba: Uint8ClampedArray | null = null;
  private baseW = 0;
  private baseH = 0;
  /** Предрасчёт для быстрой локальной перекраски. */
  private pxHsv: Float32Array | null = null; // h,s,v на пиксель
  private wallWeight: Float32Array | null = null; // 0..1 «стенность»
  private baseColorHsv: [number, number, number] = [0, 0, 0];

  private curHex = "";
  private showingOriginal = false;
  busy = false;
  hasRender = false;

  constructor(display: HTMLCanvasElement, hooks: EngineHooks = {}) {
    this.display = display;
    const ctx = display.getContext("2d", { willReadFrequently: true });
    if (!ctx) throw new Error("no 2d context");
    this.dctx = ctx;
    this.hooks = hooks;
  }

  private setBusy(b: boolean) { this.busy = b; this.hooks.onBusy?.(b); }

  private fit(w: number, h: number) {
    if (this.display.width !== w) this.display.width = w;
    if (this.display.height !== h) this.display.height = h;
  }

  /** Загрузить оригинал (File или URL). Сбрасывает рендер: показываем просто фото. */
  async load(src: File | string): Promise<boolean> {
    const url = typeof src === "string" ? src : URL.createObjectURL(src);
    try {
      const img = await loadImage(url);
      this.origImage = img;
      this.baseRgba = null;
      this.pxHsv = null;
      this.wallWeight = null;
      this.hasRender = false;
      this.showingOriginal = false;
      this.curHex = "";
      this.fit(img.naturalWidth, img.naturalHeight);
      this.dctx.drawImage(img, 0, 0);
      this.hooks.onHasRender?.(false);
      return true;
    } catch {
      this.hooks.onError?.();
      return false;
    } finally {
      if (typeof src !== "string") URL.revokeObjectURL(url);
    }
  }

  /** Текущее оригинальное фото как Blob — чтобы отправить на бэкенд. */
  async originalBlob(): Promise<Blob | null> {
    const img = this.origImage;
    if (!img) return null;
    const c = document.createElement("canvas");
    c.width = img.naturalWidth; c.height = img.naturalHeight;
    c.getContext("2d")?.drawImage(img, 0, 0);
    return new Promise((res) => c.toBlob((b) => res(b), "image/jpeg", 0.92));
  }

  /** Принять результат ИИ (data-URL) для цвета colorHex: показать и подготовить локальную перекраску. */
  async setRender(dataUrl: string, colorHex: string): Promise<void> {
    const img = await loadImage(dataUrl);
    const w = img.naturalWidth, h = img.naturalHeight;
    const off = document.createElement("canvas");
    off.width = w; off.height = h;
    const octx = off.getContext("2d", { willReadFrequently: true })!;
    octx.drawImage(img, 0, 0);
    const data = octx.getImageData(0, 0, w, h).data;

    this.baseRgba = new Uint8ClampedArray(data);
    this.baseW = w; this.baseH = h;
    this.baseColorHsv = rgbToHsv(...hexToRgb(colorHex));

    // предрасчёт HSV и маски стен — один раз на рендер
    const n = w * h;
    const hsv = new Float32Array(n * 3);
    const weight = new Float32Array(n);
    const H0 = this.baseColorHsv[0];
    for (let i = 0, p = 0; i < n; i++, p += 4) {
      const [hh, ss, vv] = rgbToHsv(data[p]!, data[p + 1]!, data[p + 2]!);
      hsv[i * 3] = hh; hsv[i * 3 + 1] = ss; hsv[i * 3 + 2] = vv;
      let dh = Math.abs(hh - H0); if (dh > 0.5) dh = 1 - dh;
      const wHue = Math.max(0, 1 - dh / HUE_BAND);
      const wSat = Math.min(1, ss / SAT_FLOOR);
      weight[i] = wHue * wSat;
    }
    this.pxHsv = hsv;
    this.wallWeight = weight;

    this.hasRender = true;
    this.showingOriginal = false;
    this.curHex = colorHex;
    this.fit(w, h);
    this.dctx.putImageData(new ImageData(new Uint8ClampedArray(data), w, h), 0, 0);
    this.hooks.onHasRender?.(true);
  }

  /** Локальная перекраска стен базового рендера в hex. Без вызова сети. */
  recolor(hex: string): void {
    this.curHex = hex;
    if (!this.hasRender || this.showingOriginal) return;
    const base = this.baseRgba, hsv = this.pxHsv, weight = this.wallWeight;
    if (!base || !hsv || !weight) return;

    // цвет совпал с базовым — показать базовый рендер как есть
    const [H1, S1, V1] = rgbToHsv(...hexToRgb(hex));
    const [, S0, V0] = this.baseColorHsv;
    const w = this.baseW, h = this.baseH, n = w * h;
    const out = new Uint8ClampedArray(base); // старт от базовых пикселей
    const sScale = S0 > 0 ? S1 / S0 : 1;
    const vScale = V0 > 0 ? V1 / V0 : 1;
    const tmp = new Uint8ClampedArray(3);
    for (let i = 0, p = 0; i < n; i++, p += 4) {
      const wt = weight[i]!;
      if (wt <= 0) continue;
      const s = Math.min(1, hsv[i * 3 + 1]! * sScale);
      const v = Math.min(1, hsv[i * 3 + 2]! * vScale);
      hsvToRgb(H1, s, v, tmp, 0);
      const t0 = tmp[0]!, t1 = tmp[1]!, t2 = tmp[2]!;
      if (wt >= 1) {
        out[p] = t0; out[p + 1] = t1; out[p + 2] = t2;
      } else {
        out[p] = base[p]! * (1 - wt) + t0 * wt;
        out[p + 1] = base[p + 1]! * (1 - wt) + t1 * wt;
        out[p + 2] = base[p + 2]! * (1 - wt) + t2 * wt;
      }
    }
    this.fit(w, h);
    this.dctx.putImageData(new ImageData(out, w, h), 0, 0);
  }

  /** Показать оригинал (до) или вернуть окрашенный результат (после). */
  showOriginal(on: boolean): void {
    if (on === this.showingOriginal) return;
    this.showingOriginal = on;
    if (on) {
      if (this.origImage) {
        this.fit(this.origImage.naturalWidth, this.origImage.naturalHeight);
        this.dctx.drawImage(this.origImage, 0, 0);
      }
    } else if (this.hasRender) {
      this.showingOriginal = false;
      this.recolor(this.curHex);
    }
  }

  dispose(): void {
    this.origImage = null;
    this.baseRgba = null;
    this.pxHsv = null;
    this.wallWeight = null;
  }
}
