import sys, numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, sys.argv[1])
import sim
from sim import box
H, W = sim.H, sim.W
L = box(sim.L, 1); A = box(sim.Aa, 1); B = box(sim.Bb, 1)

def poly(points):
    img = Image.new("L", (W, H), 0); ImageDraw.Draw(img).polygon(points, fill=255)
    return np.asarray(img) > 0
def rect(x0, y0, x1, y1):
    m = np.zeros((H, W), bool); m[y0:y1, x0:x1] = True; return m

top = lambda x: 1 + 0.385 * x + 3
left_poly = poly([(0, top(0)), (259, top(259)), (259, 420), (248, 420), (246, 406), (232, 406), (200, 412), (150, 416), (0, 418)]) & ~rect(0, 86, 115, 431)
right_poly = poly([(492, 94), (504, 99), (584, 99), (584, 190), (536, 190), (536, 404), (504, 405), (503, 418), (492, 418)]) | poly([(536, 99), (584, 99), (584, 190), (536, 190)])
left_col = (L > 52) & (np.abs(A) < 7) & (B > 0) & (B < 22) & (sim.Aa > -3.5) & (sim.L > 50)
right_col = (L > 60) & (np.abs(A) < 7) & (B > -3) & (B < 20)
yy = np.arange(H)[:, None] + np.zeros((1, W), int)
# ниже веток (y > 360) у угла стена в тени — порог яркости мягче, листьев там нет
left_col_low = (L > 42) & (np.abs(A) < 7) & (B > 0) & (B < 24) & (yy > 360)
left = left_poly & (left_col | left_col_low)
right = right_poly & right_col

def clean(m, min_hole=60):
    # заполнить мелкие дыры (шум, блики) внутри маски
    from collections import deque
    inv = ~m; seen = np.zeros_like(m); out = m.copy()
    for y in range(H):
        for x in range(W):
            if inv[y, x] and not seen[y, x]:
                comp = []; q = deque([(x, y)]); seen[y, x] = True; edge = False
                while q:
                    cx, cy = q.popleft(); comp.append((cx, cy))
                    if cx in (0, W-1) or cy in (0, H-1): edge = True
                    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                        nx, ny = cx+dx, cy+dy
                        if 0 <= nx < W and 0 <= ny < H and inv[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True; q.append((nx, ny))
                if not edge and len(comp) < min_hole:
                    for cx, cy in comp: out[cy, cx] = True
    return out
walls = clean(left | right | sim.mk)
print("left", int(left.sum()), "right", int(right.sum()), "navy", int(sim.mk.sum()), "total", int(walls.sum()))
np.save(sys.argv[1]+"/walls.npy", walls)
vis = sim.im.astype(np.uint8).copy()
vis[walls] = (vis[walls]*0.35 + np.array([255, 0, 160])*0.65).astype(np.uint8)
Image.fromarray(vis).save(sys.argv[1]+"/walls_try2.png")
