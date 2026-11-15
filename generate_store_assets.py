#!/usr/bin/env python3
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_store_icon():
    src_icon_path = "app/src/main/res/drawable/chibipet_icon_1794675752910.jpg"
    out_icon_path = "ic_launcher_512.png"
    
    # Open source icon and resize with high quality Lanczos filter to 512x512
    im = Image.open(src_icon_path).convert("RGBA")
    im_512 = im.resize((512, 512), Image.Resampling.LANCZOS)
    im_512.save(out_icon_path, "PNG")
    print(f"Created {out_icon_path} (512x512)")
    
    # Also update mipmap xxxhdpi, xxhdpi, xhdpi, hdpi, mdpi if needed
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }
    for folder, size in sizes.items():
        dst_folder = os.path.join("app/src/main/res", folder)
        os.makedirs(dst_folder, exist_ok=True)
        # Create circular/rounded icon for mipmaps
        resized = im.resize((size, size), Image.Resampling.LANCZOS)
        # Save round icon
        mask = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        round_icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        round_icon.paste(resized, (0, 0), mask=mask)
        round_icon.save(os.path.join(dst_folder, "ic_launcher_round.png"), "PNG")
        resized.save(os.path.join(dst_folder, "ic_launcher.png"), "PNG")

def create_feature_graphic():
    # Google Play Store feature graphic: 1024 x 500, JPG or PNG (no alpha)
    out_path = "feature_graphic.png"
    
    bg_path = "app/src/main/res/drawable/img_welcome_meadow_1794675773694.jpg"
    pet_path = "app/src/main/res/drawable/chibipet_icon_1794675752910.jpg"
    
    bg = Image.open(bg_path).convert("RGBA")
    # Resize and crop bg to 1024x500
    target_w, target_h = 1024, 500
    bg_ratio = bg.width / bg.height
    target_ratio = target_w / target_h
    
    if bg_ratio > target_ratio:
        new_h = target_h
        new_w = int(new_h * bg_ratio)
    else:
        new_w = target_w
        new_h = int(new_w / bg_ratio)
        
    bg_resized = bg.resize((new_w, new_h), Image.Resampling.LANCZOS)
    left = (new_w - target_w) // 2
    top = (new_h - target_h) // 2
    bg_crop = bg_resized.crop((left, top, left + target_w, top + target_h))
    
    # Add a soft dark/colorful gradient overlay on the left for text readability
    overlay = Image.new("RGBA", (target_w, target_h), (0, 0, 0, 0))
    draw_overlay = ImageDraw.Draw(overlay)
    for x in range(target_w):
        # gradient from x=0 (alpha 180) to x=650 (alpha 0)
        if x < 650:
            alpha = int(175 * (1.0 - (x / 650.0) ** 1.3))
            draw_overlay.line([(x, 0), (x, target_h)], fill=(20, 15, 35, alpha))
    
    # Also subtle bottom gradient
    for y in range(target_h):
        if y > 380:
            alpha = int(120 * ((y - 380) / 120.0))
            draw_overlay.line([(0, y), (target_w, y)], fill=(10, 8, 20, alpha))
            
    canvas = Image.alpha_composite(bg_crop, overlay)
    
    # Load pet avatar as a circular card badge on the right
    pet_img = Image.open(pet_path).convert("RGBA")
    badge_size = 280
    pet_badge = pet_img.resize((badge_size, badge_size), Image.Resampling.LANCZOS)
    
    # Circular mask with border
    mask = Image.new('L', (badge_size, badge_size), 0)
    draw_mask = ImageDraw.Draw(mask)
    draw_mask.ellipse((0, 0, badge_size, badge_size), fill=255)
    
    # Drop shadow for badge
    shadow_offset = 8
    badge_x = target_w - badge_size - 60
    badge_y = (target_h - badge_size) // 2
    
    shadow_img = Image.new("RGBA", (target_w, target_h), (0, 0, 0, 0))
    draw_shadow = ImageDraw.Draw(shadow_img)
    draw_shadow.ellipse(
        (badge_x - 4 + shadow_offset, badge_y - 4 + shadow_offset, 
         badge_x + badge_size + 4 + shadow_offset, badge_y + badge_size + 4 + shadow_offset),
        fill=(0, 0, 0, 100)
    )
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(12))
    canvas = Image.alpha_composite(canvas, shadow_img)
    
    # Draw glowing border circle
    border_badge = Image.new("RGBA", (badge_size + 16, badge_size + 16), (0, 0, 0, 0))
    draw_border = ImageDraw.Draw(border_badge)
    draw_border.ellipse((0, 0, badge_size + 16, badge_size + 16), fill=(255, 255, 255, 230))
    canvas.paste(border_badge, (badge_x - 8, badge_y - 8), mask=border_badge)
    canvas.paste(pet_badge, (badge_x, badge_y), mask=mask)
    
    # Load fonts
    font_bold = "app/src/main/res/font/fredoka.ttf"
    font_regular = "app/src/main/res/font/plus_jakarta_sans.ttf"
    
    try:
        title_font = ImageFont.truetype(font_bold, 58)
        sub_font = ImageFont.truetype(font_bold, 28)
        tag_font = ImageFont.truetype(font_regular, 18)
    except Exception:
        title_font = ImageFont.load_default()
        sub_font = ImageFont.load_default()
        tag_font = ImageFont.load_default()
        
    draw = ImageDraw.Draw(canvas)
    
    # Title with subtle shadow
    text_x = 55
    text_y = 70
    draw.text((text_x + 2, text_y + 3), "ChibiPet", font=title_font, fill=(15, 10, 25, 180))
    draw.text((text_x, text_y), "ChibiPet", font=title_font, fill=(255, 240, 245, 255))
    
    # Subtitle
    sub_y = text_y + 72
    draw.text((text_x + 2, sub_y + 2), "3D Habit Companion", font=sub_font, fill=(15, 10, 25, 180))
    draw.text((text_x, sub_y), "3D Habit Companion", font=sub_font, fill=(255, 215, 130, 255))
    
    # Feature description
    desc_y = sub_y + 44
    desc_font = ImageFont.truetype(font_regular, 20) if os.path.exists(font_regular) else tag_font
    desc_lines = [
        "Interactive 3D Virtual Pet  *  Daily Habit Quests",
        "Photo Proof with Gemini AI  *  3D Outfits & Stickers"
    ]
    for i, line in enumerate(desc_lines):
        draw.text((text_x + 1, desc_y + i * 28 + 1), line, font=desc_font, fill=(10, 5, 20, 200))
        draw.text((text_x, desc_y + i * 28), line, font=desc_font, fill=(240, 245, 255, 245))
        
    # Badges / Pills on the bottom left
    pill_y = desc_y + 75
    badges = ["* 4 Pet Species", "* Gemini Vision Proof", "* 3D Wardrobe", "* Cloud Sync"]
    pill_x = text_x
    for badge in badges:
        bbox = draw.textbbox((0, 0), badge, font=tag_font)
        pw = bbox[2] - bbox[0] + 24
        ph = bbox[3] - bbox[1] + 14
        
        # Draw rounded pill
        pill_overlay = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
        pill_draw = ImageDraw.Draw(pill_overlay)
        pill_draw.rounded_rectangle((0, 0, pw, ph), radius=12, fill=(255, 255, 255, 45), outline=(255, 255, 255, 120), width=1)
        canvas.paste(pill_overlay, (pill_x, pill_y), mask=pill_overlay)
        draw.text((pill_x + 12, pill_y + 6), badge, font=tag_font, fill=(255, 255, 255, 255))
        
        pill_x += pw + 10
        if pill_x > 580:
            pill_x = text_x
            pill_y += ph + 8
            
    # Final image must be RGB (no alpha) for Google Play feature graphic
    final_rgb = canvas.convert("RGB")
    final_rgb.save(out_path, "PNG")
    print(f"Created {out_path} ({target_w}x{target_h})")

if __name__ == "__main__":
    create_store_icon()
    create_feature_graphic()
