/**
 * Подсказка жеста: значение плавно (ease-out cubic) едет from → to. Останавливается, как только
 * человек сам тронул контрол (isTouched). Возвращает отмену.
 */
export function sweepHint(
  set: (v: number) => void,
  from: number,
  to: number,
  duration: number,
  isTouched: () => boolean,
): () => void {
  const t0 = performance.now();
  let raf = 0;
  const step = (now: number) => {
    if (isTouched()) return;
    const k = Math.min(1, (now - t0) / duration), e = 1 - Math.pow(1 - k, 3);
    set(from + (to - from) * e);
    if (k < 1) raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}

export const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));
