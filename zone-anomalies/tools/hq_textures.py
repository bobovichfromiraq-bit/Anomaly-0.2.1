"""Высококачественные текстуры v0.4.0 (перезаписывают базовые из generate_assets.py)."""
import json
import math
import os
import random

import numpy as np
from PIL import Image
from scipy.ndimage import zoom

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
A = os.path.join(ROOT, "src", "main", "resources", "assets", "anomalies", "textures")
VFX, BLOCK, ITEM = (os.path.join(A, d) for d in ("vfx", "block", "item"))


def fbm(size, seed, octaves=5, base=4):
    """Идеально бесшовный шум: белый шум, отфильтрованный в частотной области (спектр 1/f^beta)."""
    rng = np.random.default_rng(seed)
    spec = np.fft.fft2(rng.standard_normal((size, size)))
    f = np.hypot(np.fft.fftfreq(size)[:, None], np.fft.fftfreq(size)[None, :]) * size   # циклов на размер
    f[0, 0] = 1.0
    beta = 1.0 + 0.15 * octaves
    amp = 1.0 / f ** beta
    amp[f < base * 0.5] = 0.0            # отсекаем слишком низкие частоты -> задаёт масштаб деталей
    amp[0, 0] = 0.0
    n = np.real(np.fft.ifft2(spec * amp))
    return (n - n.min()) / (n.max() - n.min() + 1e-9)


def save_add(rgb, path):
    rgb = np.clip(rgb, 0, 1)
    img = np.dstack([(rgb[..., c] * 255).astype(np.uint8) for c in range(3)] + [np.full(rgb.shape[:2], 255, np.uint8)])
    Image.fromarray(img, "RGBA").save(path)


def grid(w, h=None):
    h = h or w
    return np.meshgrid(np.arange(w) / w, np.arange(h) / h)


def gravity_swirl():
    S = 256
    u, v = grid(S)
    w1, w2 = fbm(S, 3), fbm(S, 11)
    s1 = np.sin(math.tau * (2 * u + 3 * v + w1 * 1.1))
    broad = np.clip((s1 - 0.0) / 1.0, 0, 1) ** 1.3
    s2 = np.sin(math.tau * (6 * u + 9 * v + w2 * 1.7))
    fine = np.clip(s2, 0, 1) ** 4 * 0.5
    core = np.clip(np.sin(math.tau * (2 * u + 3 * v + w1 * 1.1 + 0.08)), 0, 1) ** 24      # тонкие яркие жилы
    dust = 0.30 + 0.70 * fbm(S, 29, 5, 6)
    val = np.clip(broad * dust * 0.75 + fine * dust + core * 0.9, 0, 1)
    save_add(np.dstack([val * 0.80, val * 0.89, val]), os.path.join(VFX, "gravity_swirl.png"))


def gravity_ring():
    W, H = 256, 128
    u, v = grid(W, H)
    band = np.exp(-((v - 0.52) / 0.10) ** 2) + 0.5 * np.exp(-((v - 0.82) / 0.04) ** 2) + 0.3 * np.exp(-((v - 0.22) / 0.05) ** 2)
    dust = 0.25 + 0.75 * zoom(fbm(W, 21, 5, 6), (H / W, 1.0), order=1)[:H, :W]
    streak = 0.65 + 0.35 * np.sin(math.tau * (u * 24 + zoom(fbm(W, 5), (H / W, 1.0), order=1)[:H, :W] * 3))
    val = band * dust * streak
    save_add(np.dstack([val * 0.78, val * 0.88, val * 0.97]), os.path.join(VFX, "gravity_ring.png"))


def electric_plasma():
    S = 256
    n = fbm(S, 33, 6, 4)
    n2 = fbm(S, 71, 5, 8)
    ridge = (1 - np.abs(2 * n - 1)) ** 7
    ridge2 = (1 - np.abs(2 * n2 - 1)) ** 10
    pv = np.clip(n ** 2 * 0.40 + ridge * 0.95 + ridge2 * 0.55, 0, 1)
    save_add(np.dstack([0.12 * pv + 0.88 * pv ** 3, 0.50 * pv + 0.50 * pv ** 3, 0.95 * pv + 0.05 * pv ** 3]), os.path.join(VFX, "electric_plasma.png"))


def acid_vapor():
    S = 256
    n = fbm(S, 41, 5, 3)
    av = np.clip((n - 0.30) * 2.4, 0, 1) ** 1.7
    save_add(np.dstack([0.52 * av, 1.0 * av, 0.20 * av]), os.path.join(VFX, "acid_vapor.png"))


def haze():
    S = 256
    n = fbm(S, 57, 4, 2)
    hv = np.clip((n - 0.25) * 1.9, 0, 1) ** 1.4 * (0.55 + 0.45 * fbm(S, 8, 3, 6))
    save_add(np.dstack([0.78 * hv, 0.95 * hv, 0.28 * hv]), os.path.join(VFX, "haze.png"))


def glow():
    S = 128
    yy, xx = np.mgrid[0:S, 0:S]
    r = np.hypot(xx - S / 2 + 0.5, yy - S / 2 + 0.5) / (S / 2)
    g = np.clip(1 - r, 0, 1) ** 2.4
    save_add(np.dstack([g, g, g]), os.path.join(VFX, "glow.png"))


def flare():
    """Звезда-вспышка: яркое ядро, 6 лучей и тонкое кольцо — как блик на фото шаровой молнии."""
    S = 256
    yy, xx = np.mgrid[0:S, 0:S]
    dx, dy = xx - S / 2 + 0.5, yy - S / 2 + 0.5
    r = np.hypot(dx, dy) / (S / 2)
    ang = np.arctan2(dy, dx)
    core = np.exp(-(r / 0.07) ** 2) * 1.4 + np.exp(-(r / 0.2) ** 2) * 0.55
    rays = np.zeros_like(r)
    for k in range(6):
        a0 = k * math.pi / 3 + 0.12
        d = np.abs(((ang - a0 + math.pi) % math.tau) - math.pi)
        d = np.minimum(d, np.abs(((ang - a0 - math.pi + math.pi) % math.tau) - math.pi))
        rays += np.exp(-(d / 0.035) ** 2) * np.clip(1 - r, 0, 1) ** 1.8 * (0.8 if k % 2 else 1.0)
    ring = np.exp(-((r - 0.62) / 0.025) ** 2) * 0.25
    v = np.clip(core + rays * 0.9 + ring, 0, 1)
    save_add(np.dstack([v * 0.82, v * 0.93, v]), os.path.join(VFX, "flare.png"))


def bolt():
    """Профиль ленты молнии: яркая середина, мягкий спад к краям (u поперёк ленты)."""
    W, H = 64, 8
    u = np.linspace(0, 1, W)[None, :].repeat(H, 0)
    v = (1 - np.abs(2 * u - 1)) ** 1.5
    save_add(np.dstack([v, v, v]), os.path.join(VFX, "bolt.png"))


def bubble():
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            band = abs(y - S / 2 + 0.5) / (S / 2)          # к полюсам плёнка плотнее
            a = 60 + int(150 * band ** 2.2)
            px[x, y] = (90, 235, 100, min(255, a))
    for (cx, cy, r) in ((18, 20, 5), (45, 40, 3), (30, 30, 2)):
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 2):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    px[x % S, y % S] = (250, 255, 240, int(255 * (1 - d / (r + 1)) ** 0.5))
    img.save(os.path.join(VFX, "bubble.png"))


def acid_pool():
    """HD-кислота 64x64, 16 кадров: глубина, каустика, дрейф, пузыри (петля без шва)."""
    S, F = 64, 16
    base1, base2 = fbm(S, 5, 4, 3), fbm(S, 19, 4, 4)
    warp = fbm(S, 44, 3, 2)
    u, v = grid(S)
    rnd = random.Random(8)
    bubs = [(rnd.randint(0, S - 1), rnd.randint(0, S - 1), i * 2) for i in range(8)]
    sheet = np.zeros((S * F, S, 4), np.uint8)
    deep, mid, light = np.array([20, 82, 12]), np.array([54, 158, 26]), np.array([146, 238, 62])
    for f in range(F):
        ph = f / F
        n1 = np.roll(base1, (f * 4) % S, axis=1)
        n2 = np.roll(base2, -(f * 4) % S, axis=0)
        d = np.clip(n1 * 0.6 + n2 * 0.4, 0, 1)
        d3 = d[..., None]
        col = np.where(d3 < 0.5, deep + (mid - deep) * (d3 / 0.5), mid + (light - mid) * ((d3 - 0.5) / 0.5))
        caus = (1 - np.abs(2 * n1 - 1)) ** 7 * 0.9 + (1 - np.abs(2 * n2 - 1)) ** 9 * 0.7
        col = col + caus[..., None] * np.array([70, 70, 60]) * 0.55
        alpha = 205 + 30 * d
        for (bx, by, start) in bubs:
            age = (f - start) % F
            dx = np.minimum(np.abs(np.arange(S)[None, :] - bx), S - np.abs(np.arange(S)[None, :] - bx))
            dy = np.minimum(np.abs(np.arange(S)[:, None] - by), S - np.abs(np.arange(S)[:, None] - by))
            dist = np.hypot(dx, dy)
            if age < 11:
                r = 1.5 + age * 0.85
                ring = np.exp(-((dist - r) / 1.0) ** 2)
                col = col + ring[..., None] * np.array([90, 100, 70]) * 0.9
                inner = np.clip(1 - dist / max(r, 1), 0, 1) * 0.12
                col = col + inner[..., None] * 60
                spec = np.exp(-(np.hypot(dx - (-r * 0.35), dy - (-r * 0.35)) / 1.2) ** 2) * (r > 3)
                col = col + spec[..., None] * 140
            elif age < 13:
                r = 10 + (age - 10) * 2.0
                ring = np.exp(-((dist - r) / 1.3) ** 2) * (1 - (age - 10) / 3)
                col = col + ring[..., None] * 110
        sheet[f * S:(f + 1) * S, :, :3] = np.clip(col, 0, 255).astype(np.uint8)
        sheet[f * S:(f + 1) * S, :, 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    path = os.path.join(BLOCK, "acid_anomaly.png")
    Image.fromarray(sheet, "RGBA").save(path)
    with open(path + ".mcmeta", "w") as fh:
        json.dump({"animation": {"frametime": 2}}, fh, indent=2)
    # иконка предмета
    first = Image.fromarray(sheet[:S], "RGBA").resize((16, 16), Image.LANCZOS)
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    src, dst = first.load(), icon.load()
    for y in range(2, 15):
        for x in range(1, 15):
            if x in (1, 14) and y in (2, 14):
                continue
            edge = x in (1, 14) or y in (2, 14)
            c = src[x, y]
            dst[x, y] = (30, 96, 18, 255) if edge else (c[0], c[1], c[2], 255)
    dst[3, 4] = (235, 255, 200, 255)
    dst[4, 4] = (200, 255, 150, 255)
    icon.save(os.path.join(ITEM, "acid_anomaly.png"))


def scorch():
    """Выжженное пятно 128x128: копоть с рваным краем и тлеющие голубые трещины."""
    S = 128
    yy, xx = np.mgrid[0:S, 0:S]
    r = np.hypot(xx - S / 2 + 0.5, yy - S / 2 + 0.5) / (S / 2)
    n = fbm(S, 13, 5, 4)
    a = np.clip(1.05 - r * 1.05 + (n - 0.5) * 0.65, 0, 1) ** 0.8
    soot = (10 + 14 * fbm(S, 3, 4, 6))
    img = np.zeros((S, S, 4), np.uint8)
    img[..., 0] = soot
    img[..., 1] = soot
    img[..., 2] = soot + 6
    img[..., 3] = (a * 225).astype(np.uint8)
    rnd = random.Random(9)
    for _ in range(26):                                   # трещины от центра
        ang = rnd.uniform(0, math.tau)
        x, y = S / 2, S / 2
        for step in range(rnd.randint(14, 46)):
            ang += rnd.uniform(-0.45, 0.45)
            x += math.cos(ang)
            y += math.sin(ang)
            xi, yi = int(x), int(y)
            if not (0 <= xi < S and 0 <= yi < S) or img[yi, xi, 3] < 40:
                break
            k = max(0.0, 1 - step / 46)
            img[yi, xi, :3] = (int(40 + 60 * k), int(110 + 90 * k), int(200 + 55 * k))
            img[yi, xi, 3] = 255
    Image.fromarray(img, "RGBA").save(os.path.join(BLOCK, "electric_scorch.png"))


def run():
    for fn in (gravity_swirl, gravity_ring, electric_plasma, acid_vapor, haze, glow, flare, bolt, bubble, acid_pool, scorch):
        fn()
