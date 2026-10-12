import sys; sys.path.insert(0, sys.argv[1])
import numpy as np
from PIL import Image
import sim
from sim import box, gam, lin, Ys, H, W, im, CB
walls = np.load(sys.argv[1]+"/walls.npy")
navy = np.asarray(Image.open(sys.argv[1]+"/navy_mask.png").convert("L").resize((W, H))) > 127
# зона бывшей синей стены (с запасом) — там синеватые смешанные пиксели тоже стена
ys, xs = np.nonzero(navy); x0, x1, y0, y1 = max(0, xs.min()-4), min(W, xs.max()+5), max(0, ys.min()-4), min(H, ys.max()+5)
zone = np.zeros((H, W), bool); zone[y0:y1, x0:x1] = True
B0 = sim.Bb
chroma = np.hypot(sim.Aa, sim.Bb)
ok = zone & (sim.L < 40) & (chroma < 8)          # тёмные малонасыщенные — смесь края предмета с синим фоном
m = walls.copy()
for _ in range(2):
    g = np.zeros_like(m)
    g[1:, :] |= navy[:-1, :] | m[:-1, :]; g[:-1, :] |= m[1:, :]; g[:, 1:] |= m[:, :-1]; g[:, :-1] |= m[:, 1:]
    m = m | (g & ok & ~walls)
print("extra px", int(m.sum() - walls.sum()))
sim.assign_new(m)
ref = np.full(Ys.shape, 0.5)
for r in sim.meta:
    sel = (sim.rid == r) & m
    ref[sim.rid == r] = np.percentile(Ys[sel], 85)
z = np.where(m, Ys/ref, 0)
num = z.copy(); den = m.astype(float); rr = max(W, H)//12
for _ in range(2): num = box(num, rr); den = box(den, rr)
bz = np.where(den > 1e-4, num/np.maximum(den, 1e-6), 1)
loc = z/np.maximum(bz, 1e-4)
dark = navy | (m & zone & (sim.L < 45))
loc = np.where(dark, 1 + (loc-1)*0.35, loc)
f = loc*np.maximum(bz, 1e-4)**0.15
f = np.clip(np.where(f < 1, 0.55+0.45*f, np.minimum(1.08, 1+0.5*(f-1))), 0.72, 1.08)
hexc = "#efeee5"
tr, tg, tb = [lin(np.array(int(hexc[i:i+2], 16), float)) for i in (1, 3, 5)]
out = np.stack([gam(tr*f), gam(tg*f), gam(tb*f)], -1)
a = box(m.astype(float), 1)[..., None]
res = (im+(out-im)*a).astype(np.uint8)
Image.fromarray(res).save(sys.argv[1]+"/demo_white.png")
Image.fromarray(res).save(sys.argv[1]+"/demo_white.webp", "WEBP", quality=88, method=6)
Image.fromarray(res).crop((220, 80, 520, 480)).resize((450, 600)).save(sys.argv[1]+"/demo_white_zoom.png")
