#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
VirtualMax — генератор фирменной иконки (shield + speech-bubble + «V»).

Создаёт:
  * Android legacy launcher-иконки (mipmap-{m,h,xh,xxh,xxxh}dpi);
  * Desktop-иконку (PNG 512) и Windows ICO (multi-size);
  * мастер-вектор desktop/assets/icon.svg.

Дизайн единый для всех платформ и совпадает с адаптивной иконкой Android
(res/drawable/ic_launcher_foreground.xml + ic_launcher_background.xml):
тёмный фон с мягким изумрудным свечением, изумрудный «щит приватности»,
белый речевой пузырёк и изумрудная литера «V».

Запуск:  python3 tools/gen_icons.py   (нужен Pillow)
"""

import math
import os

from PIL import Image, ImageDraw

# --------------------------------------------------------------------- #
# Палитра (совпадает с res/values/colors.xml)
# --------------------------------------------------------------------- #
BG_TOP = (13, 20, 22)        # тёмный графит с зеленоватым отливом
BG_BOTTOM = (5, 8, 10)       # почти чёрный (#05080a)
GLOW = (0, 230, 118)         # акцентный изумруд #00e676
SHIELD_TOP = (35, 240, 143)  # светлый изумруд
SHIELD_BOTTOM = (0, 178, 95) # глубокий изумруд #00b25f
WHITE = (255, 255, 255)
V_COLOR = (0, 167, 93)       # «V» на белом пузырьке

DENSITIES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MOBILE_DIR = os.path.dirname(SCRIPT_DIR)
RES_DIR = os.path.join(MOBILE_DIR, 'app', 'src', 'main', 'res')
DESKTOP_ASSETS = os.path.join(os.path.dirname(MOBILE_DIR), 'desktop', 'assets')


def vertical_gradient(size, top, bottom):
    """RGBA-изображение size×size с вертикальным градиентом top→bottom."""
    img = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(img)
    last = size - 1
    for y in range(size):
        t = y / last
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        draw.line([(0, y), (size, y)], fill=color)
    return img


def quad_bezier(p0, p1, p2, steps=32):
    """Квадратичная кривая Безье, точки в пиксельных координатах."""
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        pts.append((x, y))
    return pts


def draw_soft_glow(img, center, radius, color, peak_alpha):
    """Мягкое радиальное свечение поверх img (без жёстких краёв)."""
    mask = Image.new('L', img.size, 0)
    md = ImageDraw.Draw(mask)
    cx, cy = center
    rings = 80
    for i in range(rings, 0, -1):
        r = radius * i / rings
        alpha = int(peak_alpha * ((1 - i / rings) ** 2))
        md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=alpha)
    overlay = Image.new('RGBA', img.size, (0, 0, 0, 0))
    overlay.paste(Image.new('RGBA', img.size, (*color, 255)), (0, 0), mask)
    img.alpha_composite(overlay)


def build_logo(size, transparent_corners=True):
    """Полное лого в пиксельном размере size×size (фон + щит + пузырёк + V)."""
    # --- фон -------------------------------------------------------------
    img = vertical_gradient(size, BG_TOP, BG_BOTTOM)
    draw = ImageDraw.Draw(img)
    # Мягкое свечение за щитом.
    draw_soft_glow(img, (size * 0.5, size * 0.46), size * 0.52, GLOW, 40)

    def pt(u, v):
        """Перевод единичных координат (0..1) в пиксели с отступом."""
        m = size * 0.06
        return (m + u * (size - 2 * m), m + v * (size - 2 * m))

    # --- щит -------------------------------------------------------------
    tl = pt(0.26, 0.26)
    tr = pt(0.74, 0.26)
    mid_r = pt(0.74, 0.50)
    mid_l = pt(0.26, 0.50)
    bottom = pt(0.50, 0.84)

    right_curve = quad_bezier(mid_r, pt(0.74, 0.70), bottom)
    left_curve = quad_bezier(bottom, pt(0.26, 0.70), mid_l)
    outline = [tl, tr, mid_r] + right_curve[1:] + left_curve[1:]

    shield_grad = vertical_gradient(size, SHIELD_TOP, SHIELD_BOTTOM)
    shield_mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(shield_mask).polygon(outline, fill=255)

    shield = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    shield.paste(shield_grad, (0, 0), shield_mask)

    # Тонкая белая внутренняя окантовка щита для чёткости на тёмном фоне.
    ImageDraw.Draw(shield).polygon(outline, outline=WHITE + (90,), width=max(2, size // 128))

    img.alpha_composite(shield)

    # --- речевой пузырёк --------------------------------------------------
    bx0, by0 = pt(0.36, 0.335)
    bx1, by1 = pt(0.64, 0.545)
    radius = 0.045 * (size * 0.88)
    tail = [pt(0.445, 0.545), pt(0.385, 0.615), pt(0.525, 0.545)]

    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([bx0, by0, bx1, by1], radius=radius, fill=WHITE)
    draw.polygon(tail, fill=WHITE)

    # --- литера «V» -------------------------------------------------------
    v_left = pt(0.435, 0.385)
    v_bottom = pt(0.50, 0.482)
    v_right = pt(0.565, 0.385)
    stroke = max(3, int(size * 0.032))
    draw.line([v_left, v_bottom, v_right], fill=V_COLOR, width=stroke, joint='curve')
    for p in (v_left, v_bottom, v_right):
        r = stroke / 2
        draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=V_COLOR)

    # --- прозрачные скруглённые углы (для legacy-иконок и desktop) --------
    if transparent_corners:
        corner = Image.new('L', (size, size), 0)
        ImageDraw.Draw(corner).rounded_rectangle(
            [0, 0, size - 1, size - 1], radius=int(size * 0.20), fill=255)
        img.putalpha(corner)

    return img


def generate_logo(final_size, out_path):
    scale = 4  # суперсэмплинг для сглаживания
    size = final_size * scale
    img = build_logo(size)
    img = img.resize((final_size, final_size), Image.Resampling.LANCZOS)
    img.save(out_path, 'PNG')
    print(f"Generated: {out_path} ({final_size}x{final_size})")


def write_master_svg(path):
    """Мастер-вектор логотипа (SVG), используется в документации."""
    svg = """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#0d1416"/>
      <stop offset="1" stop-color="#05080a"/>
    </linearGradient>
    <linearGradient id="shield" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#23f08f"/>
      <stop offset="1" stop-color="#00b25f"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.46" r="0.55">
      <stop offset="0" stop-color="#00e676" stop-opacity="0.16"/>
      <stop offset="1" stop-color="#00e676" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect x="0" y="0" width="512" height="512" rx="102" fill="url(#bg)"/>
  <rect x="0" y="0" width="512" height="512" rx="102" fill="url(#glow)"/>
  <path d="M133 133 H379 V256 C379 307 354 348 256 430 C158 348 133 307 133 256 Z"
        fill="url(#shield)" stroke="#ffffff" stroke-opacity="0.35" stroke-width="10"/>
  <path d="M184 171 H328 A23 23 0 0 1 351 194 V256 A23 23 0 0 1 328 279 H184 A23 23 0 0 1 161 256 V194 A23 23 0 0 1 184 171 Z"
        fill="#ffffff"/>
  <path d="M228 201 L207 248 L269 231 Z" fill="#ffffff"/>
  <path d="M223 197 L256 247 L289 197" fill="none" stroke="#00a75d"
        stroke-width="17" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
"""
    with open(path, 'w', encoding='utf-8') as fh:
        fh.write(svg)
    print(f"Generated: {path}")


def main():
    os.makedirs(DESKTOP_ASSETS, exist_ok=True)

    # Android legacy launcher-иконки.
    for folder, final_size in DENSITIES.items():
        dir_path = os.path.join(RES_DIR, folder)
        os.makedirs(dir_path, exist_ok=True)
        generate_logo(final_size, os.path.join(dir_path, 'ic_launcher.png'))

    # Desktop PNG + ICO + мастер-SVG.
    generate_logo(512, os.path.join(DESKTOP_ASSETS, 'icon.png'))
    icon_img = Image.open(os.path.join(DESKTOP_ASSETS, 'icon.png'))
    icon_img.save(
        os.path.join(DESKTOP_ASSETS, 'icon.ico'), format='ICO',
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("Generated: desktop/assets/icon.ico")
    write_master_svg(os.path.join(DESKTOP_ASSETS, 'icon.svg'))
    print("Done.")


if __name__ == '__main__':
    main()
