"""Порт web/src/lib/motion/roller.ts (демо-маска → регионы → покраска) для отладки."""
import sys, numpy as np
from PIL import Image
from collections import Counter

A = "/Users/levrimerov/Desktop/TzevaBest/web/public/assets/motion/"
im = np.asarray(Image.open(A+"demo.webp").convert("RGB")).astype(np.float64)
H, W, _ = im.shape
mk = np.asarray(Image.open(A+"demomask.png").convert("L").resize((W, H), Image.BILINEAR)) > 127

def lin(c):
    c = c/255.0
    return np.where(c <= 0.04045, c/12.92, ((c+0.055)/1.055)**2.4)
def gam(v):
    v = np.clip(v, 0, 1)
    return np.round(255*np.where(v <= 0.0031308, 12.92*v, 1.055*v**(1/2.4)-0.055))
def flab(t): return np.where(t > 0.008856, np.cbrt(t), 7.787*t+16/116)
def finv(t):
    t3 = t**3
    return np.where(t3 > 0.008856, t3, (t-16/116)/7.787)
def box(a, r):
    p = np.pad(a, r, mode="edge"); k = 2*r+1
    c = np.cumsum(np.cumsum(np.pad(p, ((1,0),(1,0))), 0), 1)
    return (c[k:, k:]-c[:-k, k:]-c[k:, :-k]+c[:-k, :-k])/(k*k)

R, G, B = lin(im[..., 0]), lin(im[..., 1]), lin(im[..., 2])
fx = flab((0.4124*R+0.3576*G+0.1805*B)/0.95047); fy = flab(0.2126*R+0.7152*G+0.0722*B); fz = flab((0.0193*R+0.1192*G+0.9505*B)/1.08883)
L = 116*fy-16; Aa = 500*(fx-fy); Bb = 200*(fy-fz)
Lb = box(L, 1); CA = box(Aa, 2); CB = box(Bb, 2)
Ys = finv((Lb+16)/116)

rid = np.zeros((H, W), np.int32); meta = {}
def assign_group(idx):
    global rid
    if not len(idx): return
    ys, xs = idx
    ma, mb, my = CA[ys, xs].mean(), CB[ys, xs].mean(), Ys[ys, xs].mean()
    nb = set()
    for dy, dx in ((0,1),(0,-1),(1,0),(-1,0)):
        yy = np.clip(ys+dy, 0, H-1); xx = np.clip(xs+dx, 0, W-1)
        nb |= set(np.unique(rid[yy, xx])) - {0}
    best, bid = 9, 0
    for r in nb:
        m = meta[r]; d = np.hypot(m[0]-ma, m[1]-mb); ratio = max(m[2], my)/max(1e-3, min(m[2], my))
        same_hue = d < 6 and np.hypot(m[0], m[1]) > 4; same_neutral = d < 9 and ratio < 4
        if d < best and (same_hue or same_neutral): best, bid = d, r
    if not bid:
        bid = len(meta)+1; meta[bid] = (ma, mb, my)
    rid[ys, xs] = bid
def assign_new(mask):
    sel = mask & (rid == 0)
    ys, xs = np.nonzero(sel)
    if not len(ys): return
    v = np.sort(Ys[ys, xs]); p10 = max(0.004, v[int(len(v)*0.1)]); p90 = v[int(len(v)*0.9)]
    print("p10 %.4f p90 %.4f ratio %.1f" % (p10, p90, p90/p10))
    if p90 > p10*8:
        th = np.sqrt(p10*p90); lo = Ys[ys, xs] < th
        assign_group((ys[lo], xs[lo])); assign_group((ys[~lo], xs[~lo]))
    else:
        assign_group((ys, xs))

def render(mask, hexc, soft=True):
    tr, tg, tb = [lin(np.array(int(hexc[i:i+2], 16), float)) for i in (1, 3, 5)]
    yref = {}
    for r in meta:
        v = Ys[(rid == r) & mask]
        yref[r] = np.percentile(v, 85) if len(v) else 0
    alpha = box(mask.astype(float), 1)
    ref = np.zeros_like(Ys)
    for r, v in yref.items(): ref[rid == r] = v
    ref[ref == 0] = 0.5
    f = Ys/ref
    if soft: f = np.where(f < 1, 0.5+0.5*f, np.minimum(1.12, 1+0.5*(f-1)))
    out = np.stack([gam(tr*f), gam(tg*f), gam(tb*f)], -1)
    a = alpha[..., None]
    return (im+(out-im)*a).astype(np.uint8)

if __name__ == "__main__":
    assign_new(mk)
    print("regions", {r: (round(m[0],1), round(m[1],1), round(m[2],4)) for r, m in meta.items()}, Counter(rid[mk].ravel().tolist()))
    for hexc, name in (("#e6d2b5", "ivory"), ("#2271b3", "blue"), ("#c1121c", "red")):
        Image.fromarray(render(mk, hexc)).save(f"{sys.argv[1]}/out_{name}.png")
    reg = np.zeros((H, W, 3), np.uint8); cols = [(0,0,0),(255,0,0),(0,255,0),(0,0,255),(255,255,0)]
    for r in meta: reg[rid == r] = cols[r % 5]
    Image.fromarray(reg).save(f"{sys.argv[1]}/regions.png")
