/* Сегментация стены: SLIC-суперпиксели + рост региона по графу соседей.
   Свет/тень меняют в основном L, поэтому допуск по L шире, чем по цветности (a,b),
   а граница с сильным перепадом (край мебели, наличник) останавливает рост. */
const SEG = (() => {
  const LIN = new Float32Array(256);
  for (let i = 0; i < 256; i++) { const c = i / 255; LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  const fLab = t => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);

  function toLab(px, N) {
    const L = new Float32Array(N), A = new Float32Array(N), B = new Float32Array(N);
    for (let i = 0, p = 0; i < N; i++, p += 4) {
      const R = LIN[px[p]], G = LIN[px[p + 1]], Bl = LIN[px[p + 2]];
      const fx = fLab((0.4124 * R + 0.3576 * G + 0.1805 * Bl) / 0.95047);
      const fy = fLab(0.2126 * R + 0.7152 * G + 0.0722 * Bl);
      const fz = fLab((0.0193 * R + 0.1192 * G + 0.9505 * Bl) / 1.08883);
      L[i] = 116 * fy - 16; A[i] = 500 * (fx - fy); B[i] = 200 * (fy - fz);
    }
    return { L, A, B };
  }

  function boxBlur(src, W, H, r) {
    const tmp = new Float32Array(W * H), out = new Float32Array(W * H), win = 2 * r + 1;
    for (let y = 0; y < H; y++) {
      let s = 0; const row = y * W;
      for (let x = -r; x <= r; x++) s += src[row + Math.min(W - 1, Math.max(0, x))];
      for (let x = 0; x < W; x++) {
        tmp[row + x] = s / win;
        s += src[row + Math.min(W - 1, x + r + 1)] - src[row + Math.max(0, x - r)];
      }
    }
    for (let x = 0; x < W; x++) {
      let s = 0;
      for (let y = -r; y <= r; y++) s += tmp[Math.min(H - 1, Math.max(0, y)) * W + x];
      for (let y = 0; y < H; y++) {
        out[y * W + x] = s / win;
        s += tmp[Math.min(H - 1, y + r + 1) * W + x] - tmp[Math.max(0, y - r) * W + x];
      }
    }
    return out;
  }

  /* Подготовка: суперпиксели, их средние и граф соседей с силой границы. */
  function build(px, W, H) {
    const N = W * H;
    const lab = toLab(px, N);
    const L = boxBlur(lab.L, W, H, 1), A = boxBlur(lab.A, W, H, 1), B = boxBlur(lab.B, W, H, 1);

    // сила перепада (Собель по L + цветности)
    const G = new Float32Array(N);
    for (let y = 1; y < H - 1; y++) for (let x = 1; x < W - 1; x++) {
      const i = y * W + x;
      const gx = (L[i + 1] - L[i - 1]) * 2 + L[i - W + 1] - L[i - W - 1] + L[i + W + 1] - L[i + W - 1];
      const gy = (L[i + W] - L[i - W]) * 2 + L[i + W - 1] - L[i - W - 1] + L[i + W + 1] - L[i - W + 1];
      const ax = A[i + 1] - A[i - 1], ay = A[i + W] - A[i - W], bx = B[i + 1] - B[i - 1], by = B[i + W] - B[i - W];
      G[i] = Math.sqrt(gx * gx + gy * gy) * 0.25 + Math.sqrt(ax * ax + ay * ay + bx * bx + by * by) * 0.9;
    }

    // SLIC
    const S = Math.max(8, Math.round(Math.sqrt(N / 2600)));
    const m = 12, inv = (m * m) / (S * S);
    const cx = [], cy = [], cl = [], ca = [], cb = [];
    for (let y = S >> 1; y < H; y += S) for (let x = S >> 1; x < W; x += S) {
      let bx = x, by = y, bg = Infinity;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = Math.min(W - 1, Math.max(0, x + dx)), yy = Math.min(H - 1, Math.max(0, y + dy));
        if (G[yy * W + xx] < bg) { bg = G[yy * W + xx]; bx = xx; by = yy; }
      }
      const i = by * W + bx; cx.push(bx); cy.push(by); cl.push(L[i]); ca.push(A[i]); cb.push(B[i]);
    }
    const K = cx.length, lab0 = new Int32Array(N).fill(-1), dist = new Float32Array(N);
    for (let it = 0; it < 7; it++) {
      dist.fill(Infinity);
      for (let k = 0; k < K; k++) {
        const x0 = Math.max(0, Math.round(cx[k] - S)), x1 = Math.min(W - 1, Math.round(cx[k] + S));
        const y0 = Math.max(0, Math.round(cy[k] - S)), y1 = Math.min(H - 1, Math.round(cy[k] + S));
        const kl = cl[k], ka = ca[k], kb = cb[k], kx = cx[k], ky = cy[k];
        for (let y = y0; y <= y1; y++) {
          const dy2 = (y - ky) * (y - ky) * inv;
          for (let x = x0, i = y * W + x0; x <= x1; x++, i++) {
            const dl = L[i] - kl, da = A[i] - ka, db = B[i] - kb;
            const d = dl * dl + da * da + db * db + dy2 + (x - kx) * (x - kx) * inv;
            if (d < dist[i]) { dist[i] = d; lab0[i] = k; }
          }
        }
      }
      const sx = new Float64Array(K), sy = new Float64Array(K), sl = new Float64Array(K), sa = new Float64Array(K), sb = new Float64Array(K), sn = new Int32Array(K);
      for (let y = 0, i = 0; y < H; y++) for (let x = 0; x < W; x++, i++) {
        const k = lab0[i]; if (k < 0) continue;
        sx[k] += x; sy[k] += y; sl[k] += L[i]; sa[k] += A[i]; sb[k] += B[i]; sn[k]++;
      }
      for (let k = 0; k < K; k++) if (sn[k]) { cx[k] = sx[k] / sn[k]; cy[k] = sy[k] / sn[k]; cl[k] = sl[k] / sn[k]; ca[k] = sa[k] / sn[k]; cb[k] = sb[k] / sn[k]; }
    }

    // связность: каждый суперпиксель — одна связная область; мелкие обрывки — к соседу
    const label = new Int32Array(N).fill(-1), q = new Int32Array(N), minSize = (S * S) >> 2;
    let next = 0;
    for (let s = 0; s < N; s++) {
      if (label[s] >= 0) continue;
      const k = lab0[s]; let head = 0, tail = 0, adj = -1;
      q[tail++] = s; label[s] = next;
      while (head < tail) {
        const i = q[head++], x = i % W, y = (i / W) | 0;
        const nb = [x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1];
        for (const j of nb) {
          if (j < 0) continue;
          if (label[j] >= 0) { if (label[j] !== next) adj = label[j]; continue; }
          if (lab0[j] === k) { label[j] = next; q[tail++] = j; }
        }
      }
      if (tail < minSize && adj >= 0) { for (let t = 0; t < tail; t++) label[q[t]] = adj; }
      else next++;
    }

    // средние по суперпикселям (по НЕразмытому Lab — для цвета; по размытому — для сравнения)
    const R = next, mL = new Float32Array(R), mA = new Float32Array(R), mB = new Float32Array(R), cnt = new Int32Array(R);
    for (let i = 0; i < N; i++) { const r = label[i]; mL[r] += L[i]; mA[r] += A[i]; mB[r] += B[i]; cnt[r]++; }
    for (let r = 0; r < R; r++) if (cnt[r]) { mL[r] /= cnt[r]; mA[r] /= cnt[r]; mB[r] /= cnt[r]; }

    // граф соседей: средняя сила перепада вдоль общей границы
    const adj = Array.from({ length: R }, () => new Map());
    const addEdge = (r1, r2, g) => {
      const e1 = adj[r1].get(r2); if (e1) { e1[0] += g; e1[1]++; } else adj[r1].set(r2, [g, 1]);
      const e2 = adj[r2].get(r1); if (e2) { e2[0] += g; e2[1]++; } else adj[r2].set(r1, [g, 1]);
    };
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = y * W + x, r = label[i];
      if (x < W - 1 && label[i + 1] !== r) addEdge(r, label[i + 1], Math.max(G[i], G[i + 1]));
      if (y < H - 1 && label[i + W] !== r) addEdge(r, label[i + W], Math.max(G[i], G[i + W]));
    }
    return { W, H, N, R, S, label, mL, mA, mB, cnt, adj, lab, blur: { L, A, B }, G };
  }

  /* Рост региона от суперпикселя под пальцем. k — «захват» 0.5…1.8. */
  function select(seg, x, y, k = 1) {
    const { label, mL, mA, mB, adj, W } = seg;
    const s0 = label[y * W + x];
    const tolC = 7 * k, tolL = 24 * k, locC = 4.2 * k, locL = 9 * k, edgeT = 9 * k;
    const sel = new Uint8Array(seg.R), queue = [s0]; sel[s0] = 1;
    const sL = mL[s0], sA = mA[s0], sB = mB[s0];
    for (let h = 0; h < queue.length; h++) {
      const r = queue[h];
      for (const [n, e] of adj[r]) {
        if (sel[n]) continue;
        const edge = e[0] / e[1];
        if (edge > edgeT) continue;
        const dC = Math.hypot(mA[n] - sA, mB[n] - sB), dL = Math.abs(mL[n] - sL);
        if (dC > tolC || dL > tolL) continue;
        const lC = Math.hypot(mA[n] - mA[r], mB[n] - mB[r]), lL = Math.abs(mL[n] - mL[r]);
        if (lC > locC || lL > locL) continue;
        sel[n] = 1; queue.push(n);
      }
    }
    return { sel, regions: queue.length };
  }

  /* Пиксельная маска из выбранных суперпикселей + заливка мелких дыр. */
  function toMask(seg, sel, into) {
    const { label, N, W, H } = seg;
    const mask = into || new Uint8Array(N);
    const add = new Uint8Array(N);
    for (let i = 0; i < N; i++) if (sel[label[i]]) add[i] = 1;
    // дыры: компоненты не-маски, не касающиеся края, меньше 0.25% кадра
    const seen = new Uint8Array(N), q = new Int32Array(N), lim = N * 0.0025;
    for (let s = 0; s < N; s++) {
      if (add[s] || seen[s]) continue;
      let head = 0, tail = 0, edge = false; q[tail++] = s; seen[s] = 1;
      while (head < tail) {
        const i = q[head++], x = i % W, y = (i / W) | 0;
        if (x === 0 || y === 0 || x === W - 1 || y === H - 1) edge = true;
        const nb = [x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1];
        for (const j of nb) if (j >= 0 && !add[j] && !seen[j]) { seen[j] = 1; q[tail++] = j; }
      }
      if (!edge && tail <= lim) for (let t = 0; t < tail; t++) add[q[t]] = 1;
    }
    let count = 0, minx = W, miny = H, maxx = 0, maxy = 0;
    for (let i = 0; i < N; i++) if (add[i] && !mask[i]) {
      mask[i] = 1; count++;
      const x = i % W, y = (i / W) | 0;
      if (x < minx) minx = x; if (x > maxx) maxx = x; if (y < miny) miny = y; if (y > maxy) maxy = y;
    }
    return count ? { count, minx, miny, maxx, maxy } : null;
  }

  /* Попиксельная доводка края: от границы маски растём по гладким пикселям той же цветности.
     Стоп — реальный контур (сильный перепад) или смена цвета. Убирает «ступеньки» суперпикселей. */
  function refine(seg, mask, k = 1) {
    const { W, H, N, G, S } = seg, { L, A, B } = seg.blur;
    let sa = 0, sb = 0, sl = 0, sl2 = 0, n = 0;
    for (let i = 0; i < N; i++) if (mask[i]) { sa += A[i]; sb += B[i]; sl += L[i]; sl2 += L[i] * L[i]; n++; }
    if (!n) return 0;
    const ma = sa / n, mb = sb / n, ml = sl / n, sdl = Math.sqrt(Math.max(0, sl2 / n - ml * ml));
    const lLo = ml - (2.2 * sdl + 3), lHi = ml + (1.6 * sdl + 2);
    const cTol = 6 * k, edgeStop = 3.6 * k, lStep = 2 * k, maxSteps = Math.round(S * 1.6);
    const steps = new Uint8Array(N), q = new Int32Array(N);
    let head = 0, tail = 0, added = 0;
    for (let i = 0; i < N; i++) {
      if (!mask[i]) continue;
      const x = i % W, y = (i / W) | 0;
      if ((x > 0 && !mask[i - 1]) || (x < W - 1 && !mask[i + 1]) || (y > 0 && !mask[i - W]) || (y < H - 1 && !mask[i + W])) q[tail++] = i;
    }
    while (head < tail) {
      const i = q[head++], x = i % W, y = (i / W) | 0, st = steps[i];
      if (st >= maxSteps) continue;
      for (let d = 0; d < 4; d++) {
        let j;
        if (d === 0) { if (x >= W - 1) continue; j = i + 1; } else if (d === 1) { if (x <= 0) continue; j = i - 1; }
        else if (d === 2) { if (y >= H - 1) continue; j = i + W; } else { if (y <= 0) continue; j = i - W; }
        if (mask[j]) continue;
        if (G[j] > edgeStop) continue;
        if (Math.abs(L[j] - L[i]) > lStep || L[j] < lLo || L[j] > lHi) continue;
        const da = A[j] - ma, db = B[j] - mb;
        if (da * da + db * db > cTol * cTol) continue;
        mask[j] = 1; steps[j] = st + 1; q[tail++] = j; added++;
      }
    }
    return added;
  }

  /* Тап по стене: выбор суперпикселей → маска → доводка края. Возвращает рамку добавленного. */
  function paintAt(seg, x, y, k, mask) {
    const before = mask.slice();
    const { sel } = select(seg, x, y, k);
    toMask(seg, sel, mask);
    refine(seg, mask, k);
    const { W, H, N } = seg;
    let count = 0, minx = W, miny = H, maxx = 0, maxy = 0;
    for (let i = 0; i < N; i++) if (mask[i] && !before[i]) {
      count++; const xx = i % W, yy = (i / W) | 0;
      if (xx < minx) minx = xx; if (xx > maxx) maxx = xx; if (yy < miny) miny = yy; if (yy > maxy) maxy = yy;
    }
    return count ? { count, minx, miny, maxx, maxy } : null;
  }

  return { build, select, toMask, refine, paintAt };
})();
if (typeof module !== 'undefined') module.exports = SEG;
