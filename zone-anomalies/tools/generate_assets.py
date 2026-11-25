#!/usr/bin/env python3
"""
Генератор ассетов мода «Zone Anomalies» v0.1.2 (текстуры, иконки, звуки).
Всё процедурное: нарисовано/синтезировано с нуля.

    pip install numpy pillow scipy
    python tools/generate_assets.py        # нужен ffmpeg для .ogg
"""
import json
import math
import os
import random
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image
from scipy import signal

import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "anomalies")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
TEX_ITEM = os.path.join(ASSETS, "textures", "item")
TEX_MISC = os.path.join(ASSETS, "textures", "misc")
TEX_EFFECT = os.path.join(ASSETS, "textures", "mob_effect")
SND = os.path.join(ASSETS, "sounds")
for d in (TEX_BLOCK, TEX_ITEM, TEX_MISC, TEX_EFFECT, SND):
    os.makedirs(d, exist_ok=True)


def mcmeta(path, **animation):
    with open(path + ".mcmeta", "w") as f:
        json.dump({"animation": animation}, f, indent=2)


# =========================================================================== #
#  ТЕКСТУРЫ
# =========================================================================== #
def arc_frame(px, rnd, yoff=0):
    """Один кадр «шаровой молнии»: от центра расходятся ветвящиеся разряды."""
    pts_all = []   # (x, y, is_main, index)

    def walk(x, y, ang, length, main):
        for i in range(length):
            ang += rnd.uniform(-0.6, 0.6)
            x += math.cos(ang)
            y += math.sin(ang)
            xi, yi = int(round(x)), int(round(y))
            if not (0 <= xi < 16 and 0 <= yi < 16):
                break
            pts_all.append((xi, yi, main, i))
            if main and i >= 2 and rnd.random() < 0.25:
                walk(x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.1), rnd.randint(2, 4), False)

    n = rnd.choice((4, 5, 5, 6))
    base = rnd.uniform(0, math.tau)
    for k in range(n):
        walk(7.5, 7.5, base + math.tau * k / n + rnd.uniform(-0.4, 0.4), 9, True)

    def alpha(x, y):
        return px[x, yoff + y][3]

    for (x, y, _, _) in pts_all:  # свечение
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and alpha(nx, ny) < 110:
                px[nx, yoff + ny] = (60, 150, 255, 110)
    for (x, y, _, _) in pts_all:  # тело разряда
        px[x, yoff + y] = (130, 225, 255, 235)
    for (x, y, main, i) in pts_all:  # раскалённая сердцевина
        if (main and i % 2 == 0) or i < 2:
            px[x, yoff + y] = (255, 255, 255, 255)
    for yy in range(5, 11):  # ядро
        for xx in range(5, 11):
            d = math.hypot(xx - 7.5, yy - 7.5)
            if d < 3.2 and alpha(xx, yy) < 200:
                px[xx, yoff + yy] = (90, 190, 255, 170) if d > 2.0 else (200, 245, 255, 235)
    for xx, yy in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[xx, yoff + yy] = (255, 255, 255, 255)


def arcs_texture(index, frames=8):
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    px = img.load()
    for f in range(frames):
        arc_frame(px, random.Random(index * 1000 + f), f * 16)
    name = f"electric_arcs_{index}.png"
    img.save(os.path.join(TEX_BLOCK, name))
    mcmeta(os.path.join(TEX_BLOCK, name), frametime=2)


def electric_core_texture():
    frames = 4
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 255))
    px = img.load()
    for f in range(frames):
        rnd = random.Random(300 + f)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5) / 8.0
                v = max(0.0, 1.0 - d) + rnd.random() * 0.25 - 0.05
                if v > 0.75:
                    c = (255, 255, 255, 255)
                elif v > 0.45:
                    c = (190, 245, 255, 255)
                elif v > 0.25:
                    c = (90, 190, 250, 255)
                else:
                    c = (40, 110, 220, 255)
                px[x, f * 16 + y] = c
    img.save(os.path.join(TEX_BLOCK, "electric_core.png"))
    mcmeta(os.path.join(TEX_BLOCK, "electric_core.png"), frametime=2, interpolate=True)


def electric_scorch_texture():
    """Обожжённое пятно на земле под шаром."""
    rnd = random.Random(77)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = max(0.0, 1.0 - d / 7.8) * 0.9 + rnd.uniform(-0.12, 0.12)
            if a > 0.18:
                shade = 14 + int(rnd.random() * 14)
                px[x, y] = (shade, shade, shade + 6, int(min(1, a) * 215))
    for _ in range(14):
        px[rnd.randint(4, 11), rnd.randint(4, 11)] = (70, 160, 230, 170)
    img.save(os.path.join(TEX_BLOCK, "electric_scorch.png"))


def acid_texture():
    """Бесшовная пузырящаяся жижа, 16 кадров. Пузыри «заворачиваются» через край."""
    frames = 16
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    px = img.load()
    nrnd = random.Random(21)
    noise = [[nrnd.random() for _ in range(16)] for _ in range(16)]
    brnd = random.Random(5)
    bubbles = [(brnd.randint(0, 15), brnd.randint(0, 15), i * 3) for i in range(6)]
    for f in range(frames):
        shift = f // 4
        for y in range(16):
            for x in range(16):
                n = noise[(y + shift) % 16][(x - shift) % 16]
                if n < 0.3:
                    c = (30, 104, 18, 215)
                elif n < 0.78:
                    c = (66, 170, 32, 220)
                else:
                    c = (116, 224, 56, 226)
                px[x, f * 16 + y] = c
        for (bx, by, start) in bubbles:
            age = (f - start) % 16
            if age < 10:
                r = 0.8 + age * 0.26
                for yy in range(by - 4, by + 5):
                    for xx in range(bx - 4, bx + 5):
                        if abs(math.hypot(xx - bx, yy - by) - r) < 0.62:
                            px[xx % 16, f * 16 + yy % 16] = (190, 255, 120, 240)
                if r > 1.6:
                    px[(bx - 1) % 16, f * 16 + (by - 1) % 16] = (240, 255, 210, 255)
            elif age in (10, 11):
                for dx, dy in ((3, 0), (-3, 0), (0, 3), (0, -3), (2, 2), (-2, -2)):
                    px[(bx + dx) % 16, f * 16 + (by + dy) % 16] = (225, 255, 170, 240)
    img.save(os.path.join(TEX_BLOCK, "acid_anomaly.png"))
    mcmeta(os.path.join(TEX_BLOCK, "acid_anomaly.png"), frametime=3)
    return img


def acid_overlay_texture():
    """Экранное покрытие 320x180. В v0.1.2 компактнее: чистый центр, короткие потёки."""
    W, H = 320, 180
    rnd = random.Random(99)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    for y in range(H):
        for x in range(W):
            nx, ny = (x / (W - 1)) * 2 - 1, (y / (H - 1)) * 2 - 1
            d = math.hypot(nx * 0.95, ny * 1.25)
            a = (max(0.0, min(1.0, (d - 0.72) / 0.6)) ** 1.2) * 0.8
            q = math.floor(a * 6 + (bayer[y % 4][x % 4] + 0.5) / 16) / 6
            if q > 0:
                s = 0.75 + 0.25 * rnd.random()
                px[x, y] = (int(70 * s), int(205 * s), int(40 * s), int(255 * min(1, q * 1.05)))
    x = 0
    while x < W:  # короткие потёки сверху
        w = rnd.randint(3, 5)
        length = rnd.randint(6, 26)
        for xx in range(x, min(W, x + w)):
            for yy in range(length):
                edge = xx in (x, x + w - 1)
                px[xx, yy] = (46, 140, 28, 245) if edge else (96, 220, 54, 240)
            px[xx, length] = (96, 220, 54, 240)
        if x + 1 < W:
            px[x + 1, 1] = (200, 255, 160, 255)
        x += w + rnd.randint(6, 26)
    for xx in range(W):  # узкая лужа снизу
        top = H - 7 - int(2.5 * math.sin(xx / 9.0) + 1.5 * math.sin(xx / 3.7))
        for yy in range(top, H):
            px[xx, yy] = (62, 175, 34, 250) if yy > top else (150, 240, 100, 250)
    for _ in range(24):  # пузыри ближе к краям
        cx = rnd.choice((rnd.randint(0, 50), rnd.randint(W - 50, W - 1)))
        cy = rnd.randint(0, H - 1)
        r = rnd.randint(2, 5)
        for yy in range(cy - r, cy + r + 1):
            for xx in range(cx - r, cx + r + 1):
                if 0 <= xx < W and 0 <= yy < H and abs(math.hypot(xx - cx, yy - cy) - r) < 0.9:
                    px[xx, yy] = (190, 255, 130, 235)
    img.save(os.path.join(TEX_MISC, "acid_overlay.png"))


def acid_effect_icon():
    art = [
        "..................", "........XX........", ".......XOOX.......", ".......XOOX.......",
        "......XOOOOX......", "......XOHOOX......", ".....XOHOOOOX.....", ".....XOHOOOOX.....",
        "....XOOOOOOOOX....", "....XOOOOOOGOX....", "....XOOOOOOGOX....", "....XOOOOOGGOX....",
        "....XOOOOGGOOX....", ".....XOOOOOOX.....", "......XXOOXX......", ".......XXXX.......",
        "..................", "..................",
    ]
    cols = {"X": (38, 110, 24, 255), "O": (96, 214, 52, 255), "H": (214, 255, 170, 255), "G": (58, 160, 34, 255)}
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in cols:
                px[x, y] = cols[ch]
    img.save(os.path.join(TEX_EFFECT, "acid_cover.png"))


# --------------------------------- иконки предметов ------------------------ #
def icon_gravity():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 15):
        t = (y - 1) / 13.0
        w = 6.6 * (1 - t) + 1.3
        for x in range(16):
            dx = x - 7.5
            if abs(dx) <= w:
                band = (dx * 0.9 + y * 1.3) % 6
                c = (242, 250, 255, 235) if band < 2 else (160, 186, 212, 220) if band < 4 else (96, 118, 144, 200)
                if abs(abs(dx) - w) < 1.0:
                    c = (62, 78, 100, 255)
                px[x, y] = c
    for x in range(4, 12):
        px[x, 15] = (40, 46, 58, 150)
    for (x, y) in ((1, 3), (14, 5), (2, 10), (13, 12), (0, 7)):
        px[x, y] = (255, 255, 255, 220)
    img.save(os.path.join(TEX_ITEM, "gravity_anomaly.png"))
    img.save(os.path.join(TEX_BLOCK, "gravity_particle.png"))


def icon_electric():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    arc_frame(img.load(), random.Random(4242), 0)
    img.save(os.path.join(TEX_ITEM, "electric_anomaly.png"))


def icon_acid(acid_img):
    src = acid_img.crop((0, 0, 16, 16)).load()
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dst = out.load()
    for y in range(2, 15):
        for x in range(1, 15):
            if (x in (1, 14)) and (y in (2, 14)):
                continue
            c = src[x, y]
            edge = x in (1, 14) or y in (2, 14)
            dst[x, y] = (30, 96, 18, 255) if edge else (c[0], c[1], c[2], 255)
    dst[3, 4] = (235, 255, 200, 255)
    dst[4, 4] = (200, 255, 150, 255)
    out.save(os.path.join(TEX_ITEM, "acid_anomaly.png"))



def _orb_icon(name, core, mid, edge, sparkle, seed, ring=None):
    """Светящийся артефакт-сфера 16x16 с бликом и искрами."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.0)
            if d < 5.2:
                t = d / 5.2
                c = core if t < 0.35 else mid if t < 0.75 else edge
                px[x, y] = (*c, 255)
            elif d < 6.4 and ring:
                px[x, y] = (*ring, 150)
    px[5, 5] = (255, 255, 255, 255)
    px[6, 5] = (255, 255, 255, 220)
    px[5, 6] = (255, 255, 255, 220)
    for _ in range(7):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(6.2, 7.6)
        x, y = int(7.5 + math.cos(a) * r), int(8 + math.sin(a) * r)
        if 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] == 0:
            px[x, y] = (*sparkle, 230)
    img.save(os.path.join(TEX_ITEM, name))


def icon_artifacts():
    _orb_icon("artifact_moonlight.png", (255, 255, 255), (200, 225, 255), (110, 140, 205), (230, 240, 255), 1, (150, 190, 255))
    _orb_icon("artifact_flash.png", (255, 255, 235), (255, 225, 90), (235, 150, 30), (120, 220, 255), 2, (255, 235, 140))
    # «Капля» — каплеобразный зелёный кристалл
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(1, 15):
        w = 1.0 + (y - 1) * 0.55 if y < 9 else max(0.0, 6.0 - (y - 9) * 1.15)
        for x in range(16):
            dx = abs(x - 7.5)
            if dx <= w:
                t = dx / max(w, 0.1)
                px[x, y] = (210, 255, 150, 255) if t < 0.3 else (96, 214, 52, 255) if t < 0.8 else (40, 120, 26, 255)
    for p in ((5, 8), (5, 9), (6, 7)):
        px[p] = (255, 255, 235, 255)
    for p in ((2, 4), (13, 6), (12, 12)):
        px[p] = (190, 255, 120, 220)
    img.save(os.path.join(TEX_ITEM, "artifact_drop.png"))


def icon_bolt():
    """Болт-гайка: металлическая гайка с отверстием."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            hexd = max(abs(dx) * 0.866 + abs(dy) * 0.5, abs(dy))
            if hexd <= 5.6:
                d = math.hypot(dx, dy)
                if d < 2.4:
                    px[x, y] = (30, 30, 34, 255)
                else:
                    shade = 150 - int(dx * 5 + dy * 5)
                    px[x, y] = (shade, shade + 4, shade + 12, 255)
    for x in range(16):
        for y in range(16):
            if px[x, y][3] and (x + y) % 7 == 0 and math.hypot(x - 7.5, y - 7.5) > 2.4:
                px[x, y] = (210, 214, 224, 255)
    img.save(os.path.join(TEX_ITEM, "bolt.png"))


def icon_detector():
    art = [
        "........A.......",
        "........A.......",
        "........A.......",
        "...DDDDDDDDDDD..",
        "..DLLLLLLLLLLLD.",
        "..DLSSSSSSSSSLD.",
        "..DLSGGGGGGGSLD.",
        "..DLSGggGGGGSLD.",
        "..DLSSSSSSSSSLD.",
        "..DLLLLLLLLLLLD.",
        "..DLBLLRLLYLBLD.",
        "..DLLLLLLLLLLLD.",
        "..DLLLLLLLLLLLD.",
        "...DDDDDDDDDDD..",
        "................",
        "................",
    ]
    cols = {"A": (176, 176, 184, 255), "D": (28, 28, 34, 255), "L": (96, 102, 114, 255),
            "S": (18, 36, 22, 255), "G": (70, 220, 90, 255), "g": (190, 255, 200, 255),
            "B": (200, 60, 50, 255), "R": (255, 70, 60, 255), "Y": (240, 200, 60, 255)}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in cols:
                px[x, y] = cols[ch]
    img.save(os.path.join(TEX_ITEM, "anomaly_detector.png"))



# --------------------------------- 3D-VFX текстуры ------------------------- #
from scipy.ndimage import zoom

TEX_VFX = os.path.join(ASSETS, "textures", "vfx")
os.makedirs(TEX_VFX, exist_ok=True)


def tnoise(size, cells, seed):
    """Бесшовный (tileable) шум."""
    g = np.random.default_rng(seed).random((cells, cells))
    n = zoom(g, size / cells, order=3, mode="grid-wrap")[:size, :size]
    return (n - n.min()) / (n.max() - n.min() + 1e-9)


def fbm(size, seed, octaves=4, base=4):
    out, amp, tot = np.zeros((size, size)), 1.0, 0.0
    for k in range(octaves):
        out += amp * tnoise(size, base * 2 ** k, seed + k * 17)
        tot += amp
        amp *= 0.5
    return out / tot


def save_rgb(arr, name):
    """Аддитивные текстуры: чёрный фон, альфа=255 (energySwirl игнорирует альфу)."""
    arr = np.clip(arr, 0, 1)
    img = np.dstack([(arr[..., c] * 255).astype(np.uint8) for c in range(3)] + [np.full(arr.shape[:2], 255, np.uint8)])
    Image.fromarray(img, "RGBA").save(os.path.join(TEX_VFX, name))


def vfx_textures():
    S = 128
    u, v = np.meshgrid(np.arange(S) / S, np.arange(S) / S)

    # воронка Gravi: закрученные потоки воздуха и пыли
    w = fbm(S, 3)
    s1 = np.sin(math.tau * (2 * u + 3 * v + w * 0.9))
    streak = np.clip((s1 - 0.05) / 0.95, 0, 1) ** 1.4
    fine = np.clip(np.sin(math.tau * (5 * u + 8 * v + w * 1.6)), 0, 1) ** 3 * 0.35
    val = (streak * (0.35 + 0.65 * fbm(S, 9)) + fine) * 0.95
    save_rgb(np.dstack([val * 0.82, val * 0.90, val * 1.0]), "gravity_swirl.png")

    # кольцо-ударная волна: плотная полоса + пыль
    band = np.exp(-((v - 0.55) / 0.13) ** 2) + 0.45 * np.exp(-((v - 0.85) / 0.05) ** 2) + 0.25 * np.exp(-((v - 0.2) / 0.06) ** 2)
    dust = 0.4 + 0.6 * fbm(S, 21)
    rv = band * dust
    save_rgb(np.dstack([rv * 0.75, rv * 0.86, rv * 0.95]), "gravity_ring.png")

    # плазма Electro: жилы + свечение
    n = fbm(S, 33, 5)
    ridge = (1 - np.abs(2 * n - 1)) ** 6
    pv = np.clip(n ** 2 * 0.55 + ridge * 1.25, 0, 1)
    save_rgb(np.dstack([0.15 * pv + 0.85 * pv ** 3, 0.55 * pv + 0.45 * pv ** 3, 0.95 * pv + 0.05 * pv ** 3]), "electric_plasma.png")

    # ядовитый пар Chemical
    n = fbm(S, 41, 4)
    av = np.clip((n - 0.33) * 2.3, 0, 1) ** 1.6
    save_rgb(np.dstack([0.50 * av, 1.0 * av, 0.22 * av]), "acid_vapor.png")

    # мягкое свечение (гало)
    yy, xx = np.mgrid[0:64, 0:64]
    r = np.hypot(xx - 31.5, yy - 31.5) / 32.0
    g = np.clip(1 - r, 0, 1) ** 2.2
    save_rgb(np.dstack([g, g, g]), "glow.png")

    # пузырь: зелёная плёнка + блики (экваториальная развёртка)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(32):
            band = abs(y - 15.5) / 16.0
            a = 70 + int(110 * band ** 2)
            px[x, y] = (90, 235, 100, min(255, a))
    for (cx, cy, rr) in ((9, 10, 3), (22, 20, 2)):
        for y in range(cy - rr, cy + rr + 1):
            for x in range(cx - rr, cx + rr + 1):
                if math.hypot(x - cx, y - cy) <= rr:
                    px[x, y] = (245, 255, 235, 235)
    img.save(os.path.join(TEX_VFX, "bubble.png"))

    # обломки (пиксельная скала)
    rnd = random.Random(12)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            b = 78 + rnd.randint(-16, 16)
            px[x, y] = (b + 8, b + 4, b, 255)
    for _ in range(10):
        px[rnd.randint(0, 15), rnd.randint(0, 15)] = (150, 146, 140, 255)
    for _ in range(4):
        px[rnd.randint(0, 15), rnd.randint(0, 15)] = (84, 190, 235, 255)
    img.save(os.path.join(TEX_VFX, "debris.png"))

    # экран: «хватка» гравитации — туннельная виньетка и скоростные линии
    W, H = 320, 180
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(5)
    rays = [rnd.uniform(0, math.tau) for _ in range(46)]
    for y in range(H):
        for x in range(W):
            nx, ny = (x / (W - 1)) * 2 - 1, (y / (H - 1)) * 2 - 1
            d = math.hypot(nx * 0.95, ny * 1.25)
            a = (max(0.0, min(1.0, (d - 0.45) / 0.7)) ** 1.3) * 0.65
            ang = math.atan2(ny, nx)
            for ra in rays:
                diff = abs((ang - ra + math.pi) % math.tau - math.pi)
                if diff < 0.025 and d > 0.4:
                    a = max(a, min(0.75, (d - 0.4) * 1.1))
            q = math.floor(a * 6 + (bayer[y % 4][x % 4] + 0.5) / 16) / 6
            if q > 0:
                px[x, y] = (150, 185, 225, int(255 * min(1, q)))
    img.save(os.path.join(TEX_MISC, "gravity_overlay.png"))


# =========================================================================== #
#  ЗВУКИ
# =========================================================================== #
SR = 44100


def env_exp(n, tau):
    return np.exp(-(np.arange(n) / SR) / tau)


def lp(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), "low")
    return signal.lfilter(b, a, x)


def hp(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], "band")
    return signal.lfilter(b, a, x)


def fade(x, fin=0.01, fout=0.05):
    x = x.copy()
    i, o = int(fin * SR), int(fout * SR)
    if i:
        x[:i] *= np.linspace(0, 1, i)
    if o:
        x[-o:] *= np.linspace(1, 0, o)
    return x


def save_ogg(name, x, gain=0.85):
    x = np.asarray(x, dtype=np.float64)
    x = x / max(1e-9, np.max(np.abs(x))) * gain
    pcm = (x * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(SND, name + ".ogg")], check=True)
    os.remove(path)


def snd_gravity_hum(seed):  # 2.6 c, на клиенте повторяется каждые 2.4 c с перекрытием
    rnd = np.random.default_rng(seed)
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    drone = (np.sin(2 * np.pi * 52 * t) + 0.6 * np.sin(2 * np.pi * 78.5 * t + 1.0) + 0.35 * np.sin(2 * np.pi * 104 * t + 2.0))
    drone *= 0.7 + 0.3 * np.sin(2 * np.pi * 1.7 * t)
    wind = bp(rnd.standard_normal(n), 180, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.9 * t + seed))
    whoosh = hp(rnd.standard_normal(n), 2200) * 0.05 * (0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t))
    return fade(drone * 0.5 + wind * 1.1 + whoosh, 0.2, 0.2)


def snd_gravity_activate():
    rnd = np.random.default_rng(55)
    n = int(1.2 * SR)
    t = np.arange(n) / SR
    env = np.sin(np.pi * np.clip(t / 1.2, 0, 1)) ** 2
    whoosh = bp(rnd.standard_normal(n), 250, 3500) * env
    boom = np.sin(2 * np.pi * (70 - 25 * t) * t) * env_exp(n, 0.35)
    return fade(whoosh * 0.9 + boom * 1.1, 0.02, 0.3)


def snd_gravity_crack(seed):
    rnd = np.random.default_rng(seed)
    n = int(0.45 * SR)
    x = np.zeros(n)
    for _ in range(rnd.integers(3, 6)):
        pos = int(rnd.uniform(0, 0.3) * SR)
        ln = int(rnd.uniform(0.02, 0.07) * SR)
        x[pos:pos + ln] += bp(rnd.standard_normal(ln), rnd.uniform(500, 1500), rnd.uniform(3000, 7000)) * env_exp(ln, 0.012) * rnd.uniform(0.6, 1.0)
    tl = int(0.12 * SR)
    tt = np.arange(tl) / SR
    x[:tl] += np.sin(2 * np.pi * (90 - 300 * tt) * tt) * env_exp(tl, 0.04) * 0.6
    return fade(x, 0.0, 0.06)


def snd_gravity_tear():
    rnd = np.random.default_rng(404)
    n = int(1.1 * SR)
    tt = np.arange(n) / SR
    boom = np.sin(2 * np.pi * (140 * tt - 100 * tt ** 1.2)) * env_exp(n, 0.25)
    squelch = lp(rnd.standard_normal(n), 900, 3) * env_exp(n, 0.18)
    crackles = np.zeros(n)
    for _ in range(18):
        pos = int(rnd.uniform(0, 0.5) * SR)
        ln = int(rnd.uniform(0.01, 0.04) * SR)
        crackles[pos:pos + ln] += bp(rnd.standard_normal(ln), 800, 5000) * env_exp(ln, 0.01)
    crack = hp(rnd.standard_normal(n), 1500) * env_exp(n, 0.02)
    return fade(boom + squelch * 0.9 + crackles * 0.6 + crack * 0.8, 0.0, 0.2)


def snd_electric_crackle(seed):  # постоянный треск, 1.9 c (повтор каждые 1.8 c)
    rnd = np.random.default_rng(seed)
    n = int(1.9 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for _ in range(int(rnd.integers(45, 70))):
        pos = int(rnd.uniform(0, 1.85) * SR)
        ln = int(rnd.uniform(0.002, 0.012) * SR)
        x[pos:pos + ln] += hp(rnd.standard_normal(ln), 2200) * env_exp(ln, 0.003) * rnd.uniform(0.2, 1.0)
    for _ in range(7):  # крупные щелчки
        pos = int(rnd.uniform(0, 1.8) * SR)
        ln = int(0.04 * SR)
        x[pos:pos + ln] += bp(rnd.standard_normal(ln), 900, 6000) * env_exp(ln, 0.01) * 1.4
    hum = (np.sin(2 * np.pi * 100 * t) + 0.5 * np.sin(2 * np.pi * 200 * t)) * 0.06 * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t))
    hiss = hp(rnd.standard_normal(n), 4500) * 0.03
    return fade(x + hum + hiss, 0.1, 0.1)


def snd_electric_zap(seed):  # удар током: низкий удар + грубое жужжание + щелчок
    rnd = np.random.default_rng(seed)
    n = int(0.9 * SR)
    tt = np.arange(n) / SR
    thump = np.sin(2 * np.pi * (80 - 40 * tt) * tt) * env_exp(n, 0.08) * 1.4
    saw = signal.sawtooth(2 * np.pi * (95 + 20 * rnd.random()) * tt)
    gate = np.repeat((rnd.random(n // 300 + 1) > 0.3).astype(float), 300)[:n]
    buzz = saw * gate * env_exp(n, 0.25)
    snap = hp(rnd.standard_normal(n), 1000) * env_exp(n, 0.02) * 2.2
    sizzle = hp(rnd.standard_normal(n), 3000) * (rnd.random(n) > 0.7) * env_exp(n, 0.3)
    return fade(thump + buzz * 0.8 + snap + sizzle * 0.9, 0.0, 0.15)


def snd_electric_idle(seed):
    rnd = np.random.default_rng(seed)
    n = int(0.7 * SR)
    x = np.zeros(n)
    for _ in range(rnd.integers(4, 9)):
        pos = int(rnd.uniform(0, 0.6) * SR)
        ln = int(rnd.uniform(0.004, 0.02) * SR)
        x[pos:pos + ln] += hp(rnd.standard_normal(ln), 2500) * env_exp(ln, 0.004) * rnd.uniform(0.3, 1.0)
    return fade(x, 0.0, 0.1)


def snd_acid_loop(seed):  # постоянное бульканье, 2.1 c (повтор каждые 2.0 c)
    rnd = np.random.default_rng(seed)
    n = int(2.1 * SR)
    x = np.zeros(n)
    for _ in range(int(rnd.integers(16, 24))):
        pos = int(rnd.uniform(0, 1.95) * SR)
        ln = int(rnd.uniform(0.04, 0.11) * SR)
        tt = np.arange(ln) / SR
        f0 = rnd.uniform(180, 520)
        x[pos:pos + ln] += np.sin(2 * np.pi * (f0 * tt + 2600 * tt ** 2)) * env_exp(ln, 0.03) * rnd.uniform(0.35, 1.0)
    x += lp(rnd.standard_normal(n), 500) * 0.12
    x += hp(rnd.standard_normal(n), 5000) * 0.015 * (rnd.random(n) > 0.5)
    return fade(x, 0.1, 0.1)


def snd_acid_bubble(seed):
    rnd = np.random.default_rng(seed)
    n = int(0.7 * SR)
    x = np.zeros(n)
    for _ in range(rnd.integers(3, 6)):
        pos = int(rnd.uniform(0, 0.45) * SR)
        ln = int(rnd.uniform(0.05, 0.12) * SR)
        tt = np.arange(ln) / SR
        x[pos:pos + ln] += np.sin(2 * np.pi * (rnd.uniform(220, 500) * tt + 2800 * tt ** 2)) * env_exp(ln, 0.035) * rnd.uniform(0.5, 1.0)
    return fade(x + lp(rnd.standard_normal(n), 700) * 0.05, 0.0, 0.1)


def snd_acid_sizzle():
    rnd = np.random.default_rng(808)
    n = int(1.1 * SR)
    tt = np.arange(n) / SR
    gate = np.repeat((rnd.random(n // 150 + 1) > 0.45).astype(float), 150)[:n]
    sizz = hp(rnd.standard_normal(n), 3500) * gate * np.exp(-tt / 0.6) * 0.8
    hiss = bp(rnd.standard_normal(n), 2000, 9000) * np.exp(-tt / 0.45) * 0.5
    bloop = np.zeros(n)
    for pos in (0.05, 0.18, 0.31):
        ln = int(0.09 * SR)
        t2 = np.arange(ln) / SR
        i = int(pos * SR)
        bloop[i:i + ln] += np.sin(2 * np.pi * (250 * t2 + 3000 * t2 ** 2)) * env_exp(ln, 0.03) * 0.7
    return fade(sizz + hiss + bloop, 0.0, 0.15)



def snd_emission_siren():  # 4 c: нарастающе-спадающая сирена
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    f = 420 + 380 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 2.0))
    phase = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase)
    return fade(x * (0.7 + 0.3 * np.sin(2 * np.pi * 6 * t)), 0.05, 0.3)


def snd_emission_rumble():  # 3.5 c: тяжёлый гул и далёкие раскаты
    rnd = np.random.default_rng(909)
    n = int(3.5 * SR)
    t = np.arange(n) / SR
    low = lp(rnd.standard_normal(n), 90, 3) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t))
    sub = np.sin(2 * np.pi * 38 * t) * 0.8 + np.sin(2 * np.pi * 55 * t) * 0.4
    crack = np.zeros(n)
    for pos in (0.4, 1.3, 2.4):
        i = int(pos * SR)
        ln = int(0.5 * SR)
        crack[i:i + ln] += lp(rnd.standard_normal(ln), 600, 2) * env_exp(ln, 0.15)
    return fade(low * 2.0 + sub + crack * 1.2, 0.3, 0.6)


def snd_artifact_spawn():  # хрустальный переливчатый звон
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k, f in enumerate((880, 1320, 1760, 2349, 3136)):
        d = int(0.08 * k * SR)
        tt = t[: n - d]
        x[d:] += np.sin(2 * np.pi * f * tt) * np.exp(-tt / (0.5 - 0.05 * k)) * (1.0 / (1 + k * 0.4))
    return fade(x, 0.003, 0.3)


def snd_detector_artifact():  # двойной «чириканье»-писк при находке артефакта
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for start, f in ((0.0, 1900), (0.12, 2500)):
        i = int(start * SR)
        ln = int(0.1 * SR)
        tt = np.arange(ln) / SR
        x[i:i + ln] += np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.03) * np.minimum(1, tt / 0.002)
    return fade(x, 0.0, 0.02)


def snd_detector_beep():
    n = int(0.14 * SR)
    t = np.arange(n) / SR
    env = np.minimum(1, t / 0.003) * np.exp(-t / 0.04)
    x = (np.sin(2 * np.pi * 1700 * t) + 0.35 * np.sin(2 * np.pi * 3400 * t) + 0.15 * np.sign(np.sin(2 * np.pi * 850 * t))) * env
    return fade(x, 0.0, 0.01)


def main():
    print("textures...")
    vfx_textures()
    electric_core_texture()
    electric_scorch_texture()
    acid_img = acid_texture()
    acid_overlay_texture()
    acid_effect_icon()
    icon_gravity()
    icon_electric()
    icon_acid(acid_img)
    icon_detector()
    import hq_textures
    hq_textures.run()          # HD-версии текстур перезаписывают базовые
    print("sounds...")
    save_ogg("gravity_hum_1", snd_gravity_hum(1), 0.8)
    save_ogg("gravity_hum_2", snd_gravity_hum(2), 0.8)
    save_ogg("gravity_activate", snd_gravity_activate(), 0.9)
    for i in (1, 2, 3):
        save_ogg(f"gravity_crack_{i}", snd_gravity_crack(10 + i))
    save_ogg("gravity_tear", snd_gravity_tear(), 0.95)
    save_ogg("electric_crackle_1", snd_electric_crackle(61), 0.8)
    save_ogg("electric_crackle_2", snd_electric_crackle(62), 0.8)
    save_ogg("electric_zap_1", snd_electric_zap(21), 0.95)
    save_ogg("electric_zap_2", snd_electric_zap(22), 0.95)
    for i in (1, 2, 3):
        save_ogg(f"electric_idle_{i}", snd_electric_idle(30 + i), 0.6)
    save_ogg("acid_loop_1", snd_acid_loop(71), 0.8)
    save_ogg("acid_loop_2", snd_acid_loop(72), 0.8)
    for i in (1, 2, 3):
        save_ogg(f"acid_bubble_{i}", snd_acid_bubble(40 + i), 0.7)
    save_ogg("acid_sizzle", snd_acid_sizzle(), 0.8)
    save_ogg("detector_beep", snd_detector_beep(), 0.7)
    save_ogg("emission_siren", snd_emission_siren(), 0.8)
    save_ogg("emission_rumble", snd_emission_rumble(), 0.9)
    print("done")


if __name__ == "__main__":
    main()
