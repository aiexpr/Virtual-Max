import os
from PIL import Image, ImageDraw

densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

base_dir = '/home/user/VirtualMax/app/src/main/res'

for folder, final_size in densities.items():
    dir_path = os.path.join(base_dir, folder)
    os.makedirs(dir_path, exist_ok=True)
    
    scale = 4
    size = final_size * scale
    
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Dark squircle background (#070a0f)
    margin = int(size * 0.04)
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=int(size * 0.22),
        fill=(7, 10, 15, 255)
    )
    
    # Max speech bubble in neon emerald green (#00e676)
    cx = size // 2
    cy = int(size * 0.46)
    r = int(size * 0.35)
    
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(0, 230, 118, 255))
    
    # Bottom-left tail
    tail_x1 = cx - int(r * 0.65)
    tail_y1 = cy + int(r * 0.5)
    tail_x2 = cx - int(r * 0.05)
    tail_y2 = cy + int(r * 0.95)
    tail_tip_x = cx - int(r * 1.02)
    tail_tip_y = cy + int(r * 1.05)
    
    draw.polygon([(tail_x1, tail_y1), (tail_tip_x, tail_tip_y), (tail_x2, tail_y2)], fill=(0, 230, 118, 255))
    draw.ellipse(
        [tail_tip_x - int(size * 0.05), tail_tip_y - int(size * 0.05),
         tail_tip_x + int(size * 0.05), tail_tip_y + int(size * 0.05)],
        fill=(0, 230, 118, 255)
    )
    
    # Center letter "V" in dark color
    letter_h = int(r * 0.72)
    stroke = max(4, int(size * 0.05))
    l_top = cy - letter_h // 2
    l_bot = cy + letter_h // 2
    v_w = int(r * 0.58)
    v_left = cx - v_w
    v_right = cx + v_w
    
    draw.line([(v_left, l_top), (cx, l_bot)], fill=(7, 10, 15, 255), width=stroke)
    draw.line([(cx, l_bot), (v_right, l_top)], fill=(7, 10, 15, 255), width=stroke)
    
    for pt in [(v_left, l_top), (cx, l_bot), (v_right, l_top)]:
        pr = stroke // 2
        draw.ellipse([pt[0] - pr, pt[1] - pr, pt[0] + pr, pt[1] + pr], fill=(7, 10, 15, 255))

    final_img = img.resize((final_size, final_size), Image.Resampling.LANCZOS)
    out_path = os.path.join(dir_path, 'ic_launcher.png')
    final_img.save(out_path, 'PNG')
    print(f"Generated VirtualMax icon: {out_path} ({final_size}x{final_size})")
