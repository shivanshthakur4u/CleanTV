"""Render the source project's simple TV mark and required Android TV text banner."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
assets = root / 'companion' / 'assets'
banner_dir = root / 'tv' / 'app' / 'src' / 'main' / 'res' / 'drawable-xhdpi'
banner_dir.mkdir(parents=True, exist_ok=True)

def mark(image, box, width):
    draw = ImageDraw.Draw(image)
    x, y, w, h = box
    draw.rounded_rectangle((x, y, x+w, y+h), radius=width*1.5, outline='#D7EDCB', width=width)
    draw.rounded_rectangle((x+w*.36, y+h+width*1.7, x+w*.64, y+h+width*2.6), radius=width/3, fill='#D7EDCB')

icon = Image.new('RGB', (1024,1024), '#162221')
mark(icon, (245,305,534,350), 33)
icon.save(assets / 'tv-home-icon.png')
foreground = Image.new('RGBA', (1024,1024))
mark(foreground, (245,305,534,350), 33)
foreground.save(assets / 'tv-home-foreground.png')

banner = Image.new('RGB', (640,360), '#162221')
mark(banner, (80,125,140,90), 9)
draw = ImageDraw.Draw(banner)
fonts = ['/System/Library/Fonts/Helvetica.ttc', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'C:/Windows/Fonts/arial.ttf']
font_path = next((p for p in fonts if Path(p).exists()), None)
font = ImageFont.truetype(font_path, 48) if font_path else ImageFont.load_default(size=48)
draw.text((258,148), 'TV HOME', font=font, fill='#F0F3EB')
banner.resize((320,180), Image.Resampling.LANCZOS).save(banner_dir / 'banner.png')
print('Rendered TV Home icon and banner.')
