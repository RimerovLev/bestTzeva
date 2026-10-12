  /* ================= ВИЗУАЛИЗАТОР: ручная покраска валиком ================= */
  const DEMO_SRC = "__DEMO__";
  const DEMO_MASK = "__DEMOMASK__";
  const vz = $('#vz'), cv = $('#vz-canvas'), ctx = cv.getContext('2d', { willReadFrequently: true });
  const ov = $('#vz-orig'), octx = ov.getContext('2d');
  const off = document.createElement('canvas'), offx = off.getContext('2d');
  const roller = $('#vz-roller'), ring = $('#vz-ring'), wrapEl = $('#vz-wrap');
  const hintEl = $('#vz-hint'), busyEl = $('#vz-busy'), busyT = $('#vz-busy-t'), dropEl = $('#vz-drop'), undoBtn = $('#vz-undo');
  const LIN = new Float32Array(256);
  for (let i = 0; i < 256; i++) { const c = i / 255; LIN[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  const GAM = new Uint8ClampedArray(4096);
  for (let i = 0; i < 4096; i++) { const c = i / 4095; GAM[i] = Math.round(255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)); }
  const fLab = t => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  const fInv = t => { const t3 = t * t * t; return t3 > 0.008856 ? t3 : (t - 16 / 116) / 7.787; };
  const gi = v => (v <= 0 ? 0 : v >= 1 ? 4095 : (v * 4095) | 0);

  let W = 0, H = 0, N = 0, orig = null, Ys, CA, CB, rid, regMeta = [null], regCount = 0, mask, alpha, result, P = null;
  let color = '#2271b3', mode = 'paint', brushPx = 28, undo = [], cmp = false, busy = false, painting = false, last = null, hinted = false, userTouched = false;
  let dirty = null;

  function boxBlur(src, r) {
    const tmp = new Float32Array(N), out = new Float32Array(N), win = 2 * r + 1;
    for (let y = 0; y < H; y++) {
      let s = 0; const row = y * W;
      for (let x = -r; x <= r; x++) s += src[row + Math.min(W - 1, Math.max(0, x))];
      for (let x = 0; x < W; x++) { tmp[row + x] = s / win; s += src[row + Math.min(W - 1, x + r + 1)] - src[row + Math.max(0, x - r)]; }
    }
    for (let x = 0; x < W; x++) {
      let s = 0;
      for (let y = -r; y <= r; y++) s += tmp[Math.min(H - 1, Math.max(0, y)) * W + x];
      for (let y = 0; y < H; y++) { out[y * W + x] = s / win; s += tmp[Math.min(H - 1, y + r + 1) * W + x] - tmp[Math.max(0, y - r) * W + x]; }
    }
    return out;
  }
  /* Освещённость (Y) и цветность каждого пикселя — чтобы краска сохраняла тени и фактуру. */
  function prepLight() {
    const L = new Float32Array(N), A = new Float32Array(N), B = new Float32Array(N);
    for (let i = 0, p = 0; i < N; i++, p += 4) {
      const R = LIN[orig[p]], G = LIN[orig[p + 1]], Bl = LIN[orig[p + 2]];
      const fx = fLab((0.4124 * R + 0.3576 * G + 0.1805 * Bl) / 0.95047), fy = fLab(0.2126 * R + 0.7152 * G + 0.0722 * Bl), fz = fLab((0.0193 * R + 0.1192 * G + 0.9505 * Bl) / 1.08883);
      L[i] = 116 * fy - 16; A[i] = 500 * (fx - fy); B[i] = 200 * (fy - fz);
    }
    const Lb = boxBlur(L, 1);
    CA = boxBlur(A, 2); CB = boxBlur(B, 2);
    Ys = new Float32Array(N);
    for (let i = 0; i < N; i++) Ys[i] = fInv((Lb[i] + 16) / 116);
  }

  /* Краска = цвет краски × освещённость относительно «светлой» части той же поверхности. Поверхности
     различаем по цвету и яркости исходника, поэтому тёмная и белая стены в одном цвете выглядят одинаково. */
  function prepare(hex) {
    const tr = LIN[parseInt(hex.slice(1, 3), 16)], tg = LIN[parseInt(hex.slice(3, 5), 16)], tb = LIN[parseInt(hex.slice(5, 7), 16)];
    const R = regCount + 1, hist = new Uint32Array(R * 256), tot = new Uint32Array(R), gh = new Uint32Array(256);
    let gt = 0;
    for (let i = 0; i < N; i++) if (mask[i]) { const b = Math.min(255, (Ys[i] * 255) | 0), r = rid[i]; hist[r * 256 + b]++; tot[r]++; gh[b]++; gt++; }
    const pct = (h, o, total) => { if (!total) return 0; const lim = total * 0.85; let c = 0; for (let b = 0; b < 256; b++) { c += h[o + b]; if (c >= lim) return (b + 0.5) / 255; } return 1; };
    const Yref = new Float32Array(R);
    for (let r = 1; r < R; r++) Yref[r] = pct(hist, r * 256, tot[r]);
    P = { tr, tg, tb, Yref, Yg: pct(gh, 0, gt) || 0.5 };
  }
  function assignGroup(list) {
    if (!list.length) return;
    let sa = 0, sb = 0, sy = 0; const nb = new Map();
    for (const i of list) {
      sa += CA[i]; sb += CB[i]; sy += Ys[i];
      const x = i % W, y = (i / W) | 0;
      for (const j of [x > 0 ? i - 1 : -1, x < W - 1 ? i + 1 : -1, y > 0 ? i - W : -1, y < H - 1 ? i + W : -1]) if (j >= 0 && rid[j]) nb.set(rid[j], 1);
    }
    const n = list.length, ma = sa / n, mb = sb / n, my = sy / n;
    let id = 0, best = 9;
    for (const [r] of nb) {
      const m = regMeta[r], d = Math.hypot(m.a - ma, m.b - mb), ratio = Math.max(m.y, my) / Math.max(1e-3, Math.min(m.y, my));
      // цветная поверхность (тот же оттенок) — объединяем даже свет с тенью; нейтральные (белая/серая) — ещё и по яркости
      const sameHue = d < 6 && Math.hypot(m.a, m.b) > 4, sameNeutral = d < 9 && ratio < 4;
      if (d < best && (sameHue || sameNeutral)) { best = d; id = r; }
    }
    if (!id) { id = Math.min(250, ++regCount); regMeta[id] = { a: ma, b: mb, y: my }; }
    for (const i of list) rid[i] = id;
  }
  function assignNew() {
    const idx = [];
    for (let i = 0; i < N; i++) if (mask[i] && !rid[i]) idx.push(i);
    if (!idx.length) return;
    const ys = idx.map(i => Ys[i]).sort((a, b) => a - b);
    const p10 = Math.max(0.004, ys[Math.floor(ys.length * 0.1)]), p90 = ys[Math.floor(ys.length * 0.9)];
    if (p90 > p10 * 8) { const th = Math.sqrt(p10 * p90); assignGroup(idx.filter(i => Ys[i] < th)); assignGroup(idx.filter(i => Ys[i] >= th)); }
    else assignGroup(idx);
  }

  function updateAlpha(x0, y0, x1, y1) {
    x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(W - 1, x1); y1 = Math.min(H - 1, y1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      let s = 0, c = 0;
      for (let dy = -1; dy <= 1; dy++) { const yy = y + dy; if (yy < 0 || yy >= H) continue; for (let dx = -1; dx <= 1; dx++) { const xx = x + dx; if (xx < 0 || xx >= W) continue; s += mask[yy * W + xx]; c++; } }
      alpha[y * W + x] = s / c;
    }
  }
  function compose(x0, y0, x1, y1) {
    x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(W - 1, x1); y1 = Math.min(H - 1, y1);
    const d = result.data, { tr, tg, tb, Yref, Yg } = P;
    for (let y = y0; y <= y1; y++) {
      let i = y * W + x0, p = i * 4;
      for (let x = x0; x <= x1; x++, i++, p += 4) {
        const al = alpha[i];
        if (al <= 0) { d[p] = orig[p]; d[p + 1] = orig[p + 1]; d[p + 2] = orig[p + 2]; d[p + 3] = 255; continue; }
        let f = Ys[i] / (Yref[rid[i]] || Yg);
        f = f < 1 ? 0.5 + 0.5 * f : Math.min(1.12, 1 + 0.5 * (f - 1));
        const r = GAM[gi(tr * f)], g = GAM[gi(tg * f)], b = GAM[gi(tb * f)];
        d[p] = orig[p] + (r - orig[p]) * al; d[p + 1] = orig[p + 1] + (g - orig[p + 1]) * al; d[p + 2] = orig[p + 2] + (b - orig[p + 2]) * al; d[p + 3] = 255;
      }
    }
  }
  function fullRender() { prepare(color); compose(0, 0, W - 1, H - 1); offx.putImageData(result, 0, 0); }
  function anyMask() { for (let i = 0; i < N; i += 3) if (mask[i]) return true; return false; }
  const nextFrame = () => new Promise(r => requestAnimationFrame(() => setTimeout(r, 0)));

  async function decodeBlob(blob) {
    try { return await createImageBitmap(blob, { imageOrientation: 'from-image' }); } catch (e) { /* старый Safari */ }
    try { return await createImageBitmap(blob); } catch (e) { /* дальше */ }
    const url = URL.createObjectURL(blob);
    try { const img = new Image(); img.src = url; await img.decode(); return img; } finally { setTimeout(() => URL.revokeObjectURL(url), 2000); }
  }
  async function loadUrl(src) { const img = new Image(); img.src = src; await img.decode(); return img; }

  async function loadSource(src, maskSrc) {
    busy = true; busyT.textContent = 'Готовим фото…'; busyEl.hidden = false;
    try {
      const bmp = src instanceof Blob ? await decodeBlob(src) : await loadUrl(src);
      const bw = bmp.naturalWidth || bmp.width, bh = bmp.naturalHeight || bmp.height;
      if (!bw || !bh) throw new Error('empty');
      const s = Math.min(1, 1100 / Math.max(bw, bh));
      W = Math.max(1, Math.round(bw * s)); H = Math.max(1, Math.round(bh * s)); N = W * H;
      cv.width = ov.width = off.width = W; cv.height = ov.height = off.height = H;
      ctx.drawImage(bmp, 0, 0, W, H); octx.drawImage(bmp, 0, 0, W, H);
      orig = ctx.getImageData(0, 0, W, H).data.slice();
      await nextFrame();
      prepLight();
      rid = new Uint8Array(N); regMeta = [null]; regCount = 0;
      mask = new Uint8Array(N); alpha = new Float32Array(N); result = ctx.createImageData(W, H);
      undo = []; undoBtn.disabled = true;
      if (maskSrc) {
        const m = await loadUrl(maskSrc), t = document.createElement('canvas'); t.width = W; t.height = H;
        const tx = t.getContext('2d', { willReadFrequently: true }); tx.drawImage(m, 0, 0, W, H);
        const md = tx.getImageData(0, 0, W, H).data;
        for (let i = 0, p = 0; i < N; i++, p += 4) mask[i] = md[p] > 127 ? 1 : 0;
        assignNew(); updateAlpha(0, 0, W - 1, H - 1);
      }
      fullRender(); ctx.drawImage(off, 0, 0);
      return true;
    } catch (e) {
      say('Не удалось открыть файл. Попробуйте фото в JPG или PNG.');
      return false;
    } finally { busy = false; busyEl.hidden = true; }
  }

  function pushUndo() { undo.push({ m: mask.slice(), r: rid.slice() }); if (undo.length > 20) undo.shift(); undoBtn.disabled = false; }
  function restore(u) { mask.set(u.m); rid.set(u.r); }
  function hideHint() { if (!hinted) { hinted = true; hintEl.classList.add('hide'); } }

  function sweepColor() {
    fullRender();
    if (reduce) { ctx.drawImage(off, 0, 0); return; }
    roller.style.setProperty('--c', color); roller.classList.add('on');
    const t0 = performance.now(), dur = 800;
    const st = now => {
      const k = Math.min(1, (now - t0) / dur), e = k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2, x = Math.max(1, Math.round(e * W));
      ctx.drawImage(off, 0, 0, x, H, 0, 0, x, H); roller.style.left = (e * 100).toFixed(2) + '%';
      if (k < 1) requestAnimationFrame(st); else { ctx.drawImage(off, 0, 0); roller.classList.remove('on'); }
    };
    requestAnimationFrame(st);
  }

  /* --- валик и ластик --- */
  function toCanvas(e) {
    const r = cv.getBoundingClientRect();
    return { x: (e.clientX - r.left) * W / r.width, y: (e.clientY - r.top) * H / r.height, s: W / r.width };
  }
  function stampMask(x, y, rad, val) {
    const r2 = rad * rad, x0 = Math.max(0, Math.floor(x - rad)), x1 = Math.min(W - 1, Math.ceil(x + rad)), y0 = Math.max(0, Math.floor(y - rad)), y1 = Math.min(H - 1, Math.ceil(y + rad));
    for (let yy = y0; yy <= y1; yy++) {
      const dy = yy - y, row = yy * W;
      for (let xx = x0; xx <= x1; xx++) { const dx = xx - x; if (dx * dx + dy * dy <= r2) { const i = row + xx; mask[i] = val; if (!val) rid[i] = 0; } }
    }
    if (!dirty) dirty = { x0, y0, x1, y1 };
    else { if (x0 < dirty.x0) dirty.x0 = x0; if (y0 < dirty.y0) dirty.y0 = y0; if (x1 > dirty.x1) dirty.x1 = x1; if (y1 > dirty.y1) dirty.y1 = y1; }
  }
  function flush() {
    if (!dirty) return;
    const { x0, y0, x1, y1 } = dirty; dirty = null;
    updateAlpha(x0 - 1, y0 - 1, x1 + 1, y1 + 1); compose(x0 - 1, y0 - 1, x1 + 1, y1 + 1);
    const bx = Math.max(0, x0 - 1), by = Math.max(0, y0 - 1);
    ctx.putImageData(result, 0, 0, bx, by, Math.min(W, x1 + 2) - bx, Math.min(H, y1 + 2) - by);
  }
  function placeRing(e) {
    const r = wrapEl.getBoundingClientRect();
    ring.style.left = (e.clientX - r.left) + 'px'; ring.style.top = (e.clientY - r.top) + 'px';
  }
  function sizeRing() { ring.style.width = ring.style.height = brushPx + 'px'; ring.classList.toggle('erase', mode === 'erase'); }

  cv.addEventListener('pointerenter', e => { if (e.pointerType === 'mouse') { placeRing(e); ring.classList.add('on'); } });
  cv.addEventListener('pointerleave', () => { if (!painting) ring.classList.remove('on'); });
  cv.addEventListener('pointerdown', e => {
    if (cmp || busy || !W) return;
    e.preventDefault();
    hideHint(); userTouched = true; stopDemo();
    if (!P) prepare(color);
    const { x, y, s } = toCanvas(e);
    pushUndo(); painting = true; last = { x, y };
    try { cv.setPointerCapture(e.pointerId); } catch (x2) { /* ignore */ }
    placeRing(e); ring.classList.add('on');
    stampMask(x, y, (brushPx / 2) * s, mode === 'paint' ? 1 : 0); flush();
  });
  cv.addEventListener('pointermove', e => {
    if (e.pointerType === 'mouse' || painting) placeRing(e);
    if (!painting) return;
    const { x, y, s } = toCanvas(e), rad = (brushPx / 2) * s, val = mode === 'paint' ? 1 : 0;
    const dist = Math.hypot(x - last.x, y - last.y), n = Math.max(1, Math.ceil(dist / Math.max(1, rad / 3)));
    for (let k = 1; k <= n; k++) stampMask(last.x + (x - last.x) * k / n, last.y + (y - last.y) * k / n, rad, val);
    last = { x, y }; flush();
  });
  const endPaint = e => {
    if (!painting) return;
    painting = false;
    if (e && e.pointerType !== 'mouse') ring.classList.remove('on');
    assignNew(); fullRender(); ctx.drawImage(off, 0, 0);
  };
  cv.addEventListener('pointerup', endPaint); cv.addEventListener('pointercancel', endPaint);

  /* --- инструменты и толщины --- */
  const HELP = {
    paint: 'Тонким обводите края у мебели и окон, широким закрашивайте большие плоскости.',
    erase: 'Проведите ластиком там, где краска залезла лишняя.'
  };
  const modeBtns = $$('#vz-modes button');
  modeBtns.forEach(b => b.addEventListener('click', () => {
    mode = b.dataset.mode; vz.dataset.mode = mode;
    modeBtns.forEach(x => x.setAttribute('aria-pressed', String(x === b)));
    $('#mode-help').textContent = HELP[mode]; sizeRing();
    if (cmp) toggleCompare(false);
  }));
  const sizeBtns = $$('#vz-sizes .size');
  sizeBtns.forEach(b => b.addEventListener('click', () => {
    brushPx = Number(b.dataset.size);
    sizeBtns.forEach(x => x.setAttribute('aria-pressed', String(x === b)));
    sizeRing();
  }));
  sizeRing();

  /* --- цвет: клик по цвету сразу перекрашивает всё закрашенное --- */
  const pkName = $('#pk-name'), pkCode = $('#pk-code'), pkChip = $('#pk-chip'), sws = $$('#vz-swatches .sw'), stripes = $$('.stripe');
  function pickColor(hex, fromUser) {
    const sw = sws.find(b => b.dataset.hex === hex);
    sws.forEach(b => b.setAttribute('aria-pressed', String(b === sw)));
    stripes.forEach(st => st.classList.toggle('on', st.dataset.hex === hex));
    if (sw) { pkName.textContent = sw.dataset.name; pkCode.textContent = `${sw.dataset.code} · ${hex.toUpperCase()}`; }
    pkChip.style.setProperty('--c', hex);
    roller.style.setProperty('--c', hex);
    const changed = hex !== color;
    color = hex;
    if (!W) return;
    if (!anyMask()) { if (fromUser) say('Цвет выбран. Проведите валиком по стене.'); return; }
    if (changed) sweepColor();
  }
  sws.forEach(b => b.addEventListener('click', () => { hideHint(); userTouched = true; stopDemo(); if (mode !== 'paint') modeBtns[0].click(); pickColor(b.dataset.hex, true); }));
  stripes.forEach(st => st.addEventListener('click', () => {
    hideHint(); userTouched = true; stopDemo(); pickColor(st.dataset.hex, true);
    const r = vz.getBoundingClientRect(); if (r.top > innerHeight * 0.6 || r.bottom < 0) vz.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'center' });
  }));

  /* демо: пока человек ничего не трогал, пример сам меняет цвет */
  let demoTimer = null, demoI = 0;
  const DEMO_ORDER = ['#c1121c', '#57a639', '#f7b500', '#2271b3'];
  function stopDemo() { clearInterval(demoTimer); demoTimer = null; }
  new IntersectionObserver(([en]) => {
    if (en.isIntersecting && !userTouched && !reduce && !demoTimer) demoTimer = setInterval(() => { if (userTouched || busy || !W) return stopDemo(); pickColor(DEMO_ORDER[demoI++ % DEMO_ORDER.length]); if (demoI >= 8) stopDemo(); }, 2600);
    else if (!en.isIntersecting) stopDemo();
  }, { threshold: 0.5 }).observe($('#vz-stage'));

  /* --- сравнение --- */
  const cmpBox = $('#vz-cmp'), cmpRange = $('#vz-cmp-range'), cmpBtn = $('#vz-compare');
  let cmpTouched = false;
  const setCmp = v => { vz.style.setProperty('--pos', v + '%'); cmpBox.style.setProperty('--pos', v + '%'); };
  function toggleCompare(on) {
    cmp = on; vz.classList.toggle('cmp', on); cmpBtn.setAttribute('aria-pressed', String(on)); ring.classList.remove('on');
    if (on) { hideHint(); userTouched = true; stopDemo(); cmpTouched = false; setCmp(50); cmpRange.value = 50; if (!reduce) sweepHint(cmpRange, v => setCmp(v.toFixed(1)), 88, 50, 1100, () => cmpTouched); }
  }
  cmpBtn.addEventListener('click', () => toggleCompare(!cmp));
  cmpRange.addEventListener('input', () => { cmpTouched = true; setCmp(cmpRange.value); });

  /* --- действия --- */
  undoBtn.addEventListener('click', () => { if (!undo.length || busy) return; restore(undo.pop()); undoBtn.disabled = !undo.length; updateAlpha(0, 0, W - 1, H - 1); fullRender(); ctx.drawImage(off, 0, 0); });
  $('#vz-clear').addEventListener('click', () => {
    if (!W || busy) return;
    if (!anyMask()) { say('Пока нечего очищать'); return; }
    pushUndo(); mask.fill(0); rid.fill(0); alpha.fill(0); fullRender(); ctx.drawImage(off, 0, 0);
    say('Краска снята. Если передумали, нажмите «Отменить».');
  });
  $('#vz-demo').addEventListener('click', async () => { if (busy) return; if (cmp) toggleCompare(false); userTouched = true; stopDemo(); if (await loadSource(DEMO_SRC, DEMO_MASK)) say('Открыт пример: гостиная'); });

  /* --- загрузка своего фото --- */
  async function openFile(file) {
    if (!file || busy) return;
    if (!/^image\//.test(file.type) && !/\.(jpe?g|png|webp|heic|heif|avif)$/i.test(file.name || '')) { say('Нужен файл изображения: JPG, PNG или WEBP.'); return; }
    if (cmp) toggleCompare(false);
    userTouched = true; stopDemo();
    if (!(await loadSource(file, null))) return;
    hinted = false; hintEl.classList.remove('hide'); hintEl.style.left = '50%'; hintEl.style.top = '32%';
    if (mode !== 'paint') modeBtns[0].click();
    say('Фото загружено. Выберите цвет и проведите валиком по стене.');
  }
  $('#vz-file').addEventListener('change', e => { openFile(e.target.files[0]); e.target.value = ''; });
  const stageEl = $('#vz-stage'); let dragDepth = 0;
  stageEl.addEventListener('dragenter', e => { e.preventDefault(); dragDepth++; dropEl.hidden = false; });
  stageEl.addEventListener('dragover', e => e.preventDefault());
  stageEl.addEventListener('dragleave', () => { dragDepth = Math.max(0, dragDepth - 1); if (!dragDepth) dropEl.hidden = true; });
  stageEl.addEventListener('drop', e => { e.preventDefault(); dragDepth = 0; dropEl.hidden = true; openFile(e.dataTransfer.files[0]); });
  document.addEventListener('paste', e => {
    const item = [...(e.clipboardData?.items || [])].find(i => i.type.startsWith('image/'));
    if (!item) return; e.preventDefault(); openFile(item.getAsFile()); vz.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'center' });
  });

