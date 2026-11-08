import os
import math
from PIL import Image, ImageDraw, ImageFont

# Colors per Claude design
BG_COLOR = (17, 17, 17, 255)         # #111111
BORDER_COLOR = (47, 52, 60, 255)     # #2F343C
DOT_COLOR = (232, 64, 43, 255)       # #E8402B
STEM_COLOR = (219, 219, 219, 255)    # #DBDBDB
TEXT_COLOR = (219, 219, 219, 255)    # #DBDBDB
MUTED_COLOR = (179, 179, 179, 255)   # #B3B3B3
CANVAS_BG = (8, 9, 11, 255)          # #08090B

FONT_BOLD = 'C:\\Windows\\Fonts\\arialbd.ttf'
FONT_REGULAR = 'C:\\Windows\\Fonts\\arial.ttf'

def create_tile_mark(size, has_tile=True, scale=1.0, supersample=4):
    ss = size * supersample
    img = Image.new('RGBA', (ss, ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    if has_tile:
        radius = int(0.232 * ss)
        border_width = max(1, int(1 * supersample))
        box = [border_width // 2, border_width // 2, ss - border_width // 2 - 1, ss - border_width // 2 - 1]
        draw.rounded_rectangle(box, radius=radius, fill=BG_COLOR, outline=BORDER_COLOR, width=border_width)
    
    # Stem: 14.5% of cap size, dot same width, gap 7.5% - 10%
    w = ss * 0.13482 * scale
    gap = ss * 0.075 * scale
    stem_h = ss * 0.40 * scale
    total_h = w + gap + stem_h
    
    top = (ss - total_h) / 2.0
    left = (ss - w) / 2.0
    
    # Dot
    dot_box = [left, top, left + w, top + w]
    draw.ellipse(dot_box, fill=DOT_COLOR)
    
    # Stem
    stem_top = top + w + gap
    stem_box = [left, stem_top, left + w, stem_top + stem_h]
    draw.rounded_rectangle(stem_box, radius=w / 2.0, fill=STEM_COLOR)
    
    return img.resize((size, size), Image.Resampling.LANCZOS)

def create_adaptive_foreground(size=432, supersample=4):
    # 432x432 canvas, safe zone is inner 264px circle
    # We place the mark centered in safe zone, total_h = 176px
    ss = size * supersample
    img = Image.new('RGBA', (ss, ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Mark target height in 432px is 176px -> in ss: 176 * supersample
    target_h = 176.0 * supersample
    # In standard proportions: total_h = w + gap + stem_h = 0.13482 + 0.075 + 0.40 = 0.60982 * unit
    unit = target_h / 0.60982
    w = unit * 0.13482
    gap = unit * 0.075
    stem_h = unit * 0.40
    
    top = (ss - target_h) / 2.0
    left = (ss - w) / 2.0
    
    # Dot
    dot_box = [left, top, left + w, top + w]
    draw.ellipse(dot_box, fill=DOT_COLOR)
    
    # Stem
    stem_top = top + w + gap
    stem_box = [left, stem_top, left + w, stem_top + stem_h]
    draw.rounded_rectangle(stem_box, radius=w / 2.0, fill=STEM_COLOR)
    
    return img.resize((size, size), Image.Resampling.LANCZOS)

def create_splash_branding(width=600, height=160, supersample=4):
    ss_w = width * supersample
    ss_h = height * supersample
    img = Image.new('RGBA', (ss_w, ss_h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    font_size = int(60 * supersample)
    font = ImageFont.truetype(FONT_BOLD, font_size)
    
    text = 'Tantra'
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    
    bbox_n = draw.textbbox((0, 0), 'n', font=font)
    stem_h = bbox_n[3] - bbox_n[1]
    stem_w = int(font_size * 0.16)
    dot_d = stem_w
    gap = int(font_size * 0.10)
    
    spacing = int(font_size * 0.08)
    total_w = stem_w + spacing + text_w
    
    start_x = (ss_w - total_w) // 2
    target_baseline = (ss_h + text_h) // 2
    text_y = target_baseline - bbox[3]
    text_x = start_x + stem_w + spacing
    
    stem_bottom = target_baseline
    stem_top = stem_bottom - stem_h
    stem_box = [start_x, stem_top, start_x + stem_w, stem_bottom]
    draw.rounded_rectangle(stem_box, radius=stem_w // 2, fill=STEM_COLOR)
    
    dot_bottom = stem_top - gap
    dot_top = dot_bottom - dot_d
    dot_box = [start_x, dot_top, start_x + dot_d, dot_bottom]
    draw.ellipse(dot_box, fill=DOT_COLOR)
    
    draw.text((text_x, text_y), text, font=font, fill=TEXT_COLOR)
    
    return img.resize((width, height), Image.Resampling.LANCZOS)

def create_social_preview(width=1200, height=630, supersample=2):
    ss_w = width * supersample
    ss_h = height * supersample
    img = Image.new('RGBA', (ss_w, ss_h), CANVAS_BG)
    draw = ImageDraw.Draw(img)
    
    # 1. Tile icon: 144x144 -> in ss: 144 * supersample
    tile_size = int(144 * supersample)
    tile_img = create_tile_mark(144, has_tile=True, scale=1.0, supersample=supersample * 2)
    tile_ss = tile_img.resize((tile_size, tile_size), Image.Resampling.LANCZOS)
    
    # Center tile horizontally, placed at y = 100 * supersample
    tile_x = (ss_w - tile_size) // 2
    tile_y = int(90 * supersample)
    img.paste(tile_ss, (tile_x, tile_y), tile_ss)
    
    # 2. iTantra Lockup text: font 68pt
    font_title = ImageFont.truetype(FONT_BOLD, int(64 * supersample))
    text_t = 'Tantra'
    bbox_t = draw.textbbox((0, 0), text_t, font=font_title)
    text_w = bbox_t[2] - bbox_t[0]
    
    bbox_n = draw.textbbox((0, 0), 'n', font=font_title)
    stem_h = bbox_n[3] - bbox_n[1]
    stem_w = int(64 * supersample * 0.16)
    dot_d = stem_w
    gap = int(64 * supersample * 0.10)
    spacing = int(64 * supersample * 0.08)
    
    total_lockup_w = stem_w + spacing + text_w
    lockup_x = (ss_w - total_lockup_w) // 2
    lockup_baseline = tile_y + tile_size + int(70 * supersample)
    
    # Stem
    stem_bottom = lockup_baseline
    stem_top = stem_bottom - stem_h
    stem_box = [lockup_x, stem_top, lockup_x + stem_w, stem_bottom]
    draw.rounded_rectangle(stem_box, radius=stem_w // 2, fill=STEM_COLOR)
    
    # Dot
    dot_bottom = stem_top - gap
    dot_top = dot_bottom - dot_d
    dot_box = [lockup_x, dot_top, lockup_x + dot_d, dot_bottom]
    draw.ellipse(dot_box, fill=DOT_COLOR)
    
    # 'Tantra'
    text_x = lockup_x + stem_w + spacing
    text_y = lockup_baseline - bbox_t[3]
    draw.text((text_x, text_y), text_t, font=font_title, fill=TEXT_COLOR)
    
    # 3. Subhead
    font_sub = ImageFont.truetype(FONT_REGULAR, int(22 * supersample))
    sub_text = 'Offline Multilingual Emergency Communication for Android'
    bbox_sub = draw.textbbox((0, 0), sub_text, font=font_sub)
    sub_w = bbox_sub[2] - bbox_sub[0]
    sub_x = (ss_w - sub_w) // 2
    sub_y = lockup_baseline + int(24 * supersample)
    draw.text((sub_x, sub_y), sub_text, font=font_sub, fill=MUTED_COLOR)
    
    # 4. Feature badges
    badges = ['ON-DEVICE', 'LOW LATENCY', 'TEN LANGUAGES', 'NO ACCOUNT REQUIRED']
    font_badge = ImageFont.truetype(FONT_BOLD, int(13 * supersample))
    badge_pads = int(12 * supersample)
    badge_gap = int(16 * supersample)
    
    badge_widths = []
    for b in badges:
        bb = draw.textbbox((0, 0), b, font=font_badge)
        badge_widths.append((bb[2] - bb[0]) + 2 * badge_pads)
    
    total_badges_w = sum(badge_widths) + badge_gap * (len(badges) - 1)
    badge_start_x = (ss_w - total_badges_w) // 2
    badge_y = sub_y + int(60 * supersample)
    badge_h = int(32 * supersample)
    
    cur_x = badge_start_x
    for i, b in enumerate(badges):
        bw = badge_widths[i]
        b_box = [cur_x, badge_y, cur_x + bw, badge_y + badge_h]
        draw.rounded_rectangle(b_box, radius=badge_h // 2, fill=(28, 30, 36, 255), outline=BORDER_COLOR, width=max(1, supersample))
        bb = draw.textbbox((0, 0), b, font=font_badge)
        tw = bb[2] - bb[0]
        th = bb[3] - bb[1]
        tx = cur_x + (bw - tw) // 2
        ty = badge_y + (badge_h - th) // 2 - int(2 * supersample)
        draw.text((tx, ty), b, font=font_badge, fill=MUTED_COLOR)
        cur_x += bw + badge_gap
    
    return img.resize((width, height), Image.Resampling.LANCZOS)

def main():
    print('Generating iTantra application icons and assets...')
    
    # 1. Android Mipmaps
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    for folder, size in densities.items():
        out_dir = os.path.join('app', 'src', 'main', 'res', folder)
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, 'ic_launcher.png')
        tile = create_tile_mark(size)
        tile.save(out_path, 'PNG')
        print(f'Saved {out_path} ({size}x{size})')
    
    # 2. Android Adaptive Icon Foreground & Logo
    nodpi_dir = os.path.join('app', 'src', 'main', 'res', 'drawable-nodpi')
    os.makedirs(nodpi_dir, exist_ok=True)
    
    adaptive_fg = create_adaptive_foreground(432)
    adaptive_path = os.path.join(nodpi_dir, 'vokie_logo_adaptive.png')
    adaptive_fg.save(adaptive_path, 'PNG')
    print(f'Saved {adaptive_path} (432x432)')
    
    logo_402 = create_tile_mark(402)
    logo_path = os.path.join(nodpi_dir, 'vokie_logo.png')
    logo_402.save(logo_path, 'PNG')
    print(f'Saved {logo_path} (402x402)')
    
    splash = create_splash_branding(600, 160)
    splash_path = os.path.join(nodpi_dir, 'vokie_splash_branding.png')
    splash.save(splash_path, 'PNG')
    print(f'Saved {splash_path} (600x160)')
    
    # 3. Website Assets
    web_dir = os.path.join('website', 'assets')
    os.makedirs(web_dir, exist_ok=True)
    
    fav = create_tile_mark(64)
    fav_path = os.path.join(web_dir, 'favicon.png')
    fav.save(fav_path, 'PNG')
    print(f'Saved {fav_path} (64x64)')
    
    touch = create_tile_mark(180)
    touch_path = os.path.join(web_dir, 'apple-touch-icon.png')
    touch.save(touch_path, 'PNG')
    print(f'Saved {touch_path} (180x180)')
    
    web_logo = create_tile_mark(402)
    web_logo_path = os.path.join(web_dir, 'vokie-logo.png')
    web_logo.save(web_logo_path, 'PNG')
    print(f'Saved {web_logo_path} (402x402)')
    
    social = create_social_preview(1200, 630)
    social_path = os.path.join(web_dir, 'vokie-social-preview.png')
    # Save as RGB PNG
    social.convert('RGB').save(social_path, 'PNG')
    print(f'Saved {social_path} (1200x630)')
    
    print('All assets successfully generated.')

if __name__ == '__main__':
    main()
