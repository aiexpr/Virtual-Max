import os
import sys
from PIL import Image, ImageDraw

densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

# Путь вычисляется относительно расположения скрипта: <repo>/mobile/tools/gen_icons.py
script_dir = os.path.dirname(os.path.abspath(__file__))
mobile_dir = os.path.dirname(script_dir)
base_res_dir = os.path.join(mobile_dir, 'app', 'src', 'main', 'res')
desktop_assets_dir = os.path.join(os.path.dirname(mobile_dir), 'desktop', 'assets')

def generate_logo(final_size, out_path):
    scale = 4
    size = final_size * scale
    
    # Фон: Глубокий черно-изумрудный (#05080a)
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    margin = int(size * 0.05)
    # Squircle background
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=int(size * 0.22),
        fill=(5, 8, 10, 255)
    )
    
    # Добавляем легкое изумрудное свечение/градиент по краям (имитация)
    # Но по запросу пользователя: "логотип основной белый а фон черно зеленый"
    
    cx = size // 2
    cy = int(size * 0.46)
    r = int(size * 0.35)
    
    # Основной пузырек - БЕЛЫЙ
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
    
    # Хвостик пузырька - БЕЛЫЙ
    tail_x1 = cx - int(r * 0.65)
    tail_y1 = cy + int(r * 0.5)
    tail_x2 = cx - int(r * 0.05)
    tail_y2 = cy + int(r * 0.95)
    tail_tip_x = cx - int(r * 1.02)
    tail_tip_y = cy + int(r * 1.05)
    
    draw.polygon([(tail_x1, tail_y1), (tail_tip_x, tail_tip_y), (tail_x2, tail_y2)], fill=(255, 255, 255, 255))
    draw.ellipse(
        [tail_tip_x - int(size * 0.05), tail_tip_y - int(size * 0.05),
         tail_tip_x + int(size * 0.05), tail_tip_y + int(size * 0.05)],
        fill=(255, 255, 255, 255)
    )
    
    # Буква "V" внутри пузырька - Изумрудная (#00e676)
    letter_h = int(r * 0.72)
    stroke = max(4, int(size * 0.07))
    l_top = cy - letter_h // 2
    l_bot = cy + letter_h // 2
    v_w = int(r * 0.58)
    v_left = cx - v_w
    v_right = cx + v_w
    
    draw.line([(v_left, l_top), (cx, l_bot)], fill=(0, 230, 118, 255), width=stroke)
    draw.line([(cx, l_bot), (v_right, l_top)], fill=(0, 230, 118, 255), width=stroke)
    
    # Сглаживание концов линий
    for pt in [(v_left, l_top), (cx, l_bot), (v_right, l_top)]:
        pr = stroke // 2
        draw.ellipse([pt[0] - pr, pt[1] - pr, pt[0] + pr, pt[1] + pr], fill=(0, 230, 118, 255))

    final_img = img.resize((final_size, final_size), Image.Resampling.LANCZOS)
    final_img.save(out_path, 'PNG')
    print(f"Generated: {out_path} ({final_size}x{final_size})")

# Генерируем для Android
for folder, final_size in densities.items():
    dir_path = os.path.join(base_res_dir, folder)
    os.makedirs(dir_path, exist_ok=True)
    generate_logo(final_size, os.path.join(dir_path, 'ic_launcher.png'))

# Генерируем для Desktop
os.makedirs(desktop_assets_dir, exist_ok=True)
generate_logo(512, os.path.join(desktop_assets_dir, 'icon.png'))

# Генерируем ICO (для Windows)
icon_img = Image.open(os.path.join(desktop_assets_dir, 'icon.png'))
icon_img.save(os.path.join(desktop_assets_dir, 'icon.ico'), format='ICO', sizes=[(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)])
print("Updated desktop icons (PNG/ICO)")
