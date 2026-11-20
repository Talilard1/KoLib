"""Draws "The Lighthouse Cat", the short sample comic that comes with KoLib, and packs it as a CBZ."""
import math, random, zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FONTS = HERE.parent.parent / 'app' / 'src' / 'main' / 'res' / 'font'
OUT = HERE / 'build' / 'comic'
W, H = 1072, 1448
INK = (24, 22, 30)
GUTTER = 22
BORDER = 6

TITLE = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 150)
SFX = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 110)
BIG = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 64)
TALK = ImageFont.truetype(str(FONTS / 'poppins_medium.ttf'), 34)
CAPTION = ImageFont.truetype(str(FONTS / 'poppins_medium.ttf'), 31)

random.seed(7)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(img, box, top, bottom):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    for y in range(y0, y1):
        d.line([(x0, y), (x1, y)], fill=lerp(top, bottom, (y - y0) / max(1, y1 - y0)))


def sea(img, box, level, colour, dark, rough=1.0, phase=0.0):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    pts = [(x0, y1)]
    for x in range(x0, x1 + 8, 8):
        y = level + math.sin(x / (38 / rough) + phase) * 10 * rough + math.sin(x / 13 + phase * 2) * 4 * rough
        pts.append((x, y))
    pts.append((x1, y1))
    d.polygon(pts, fill=colour)
    for row in range(1, 6):
        yy = level + row * (y1 - level) / 6
        for x in range(x0 + (row * 37) % 60, x1, 90):
            w = 40 + (row * 13) % 30
            d.arc([x, yy - 8 * rough, x + w, yy + 8 * rough], 200, 340, fill=dark, width=3)


def stars(img, box, n=60):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y = random.randint(x0, x1), random.randint(y0, y1)
        r = random.choice([1, 1, 2, 2, 3])
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 250, 225))


def moon(img, cx, cy, r, sky):
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 244, 205))
    d.ellipse([cx - r + r * 0.55, cy - r - r * 0.15, cx + r + r * 0.55, cy + r - r * 0.15], fill=sky)


def rocks(img, base_x, base_y, s, colour=(58, 56, 66)):
    d = ImageDraw.Draw(img)
    d.polygon([(base_x - 230 * s, base_y + 40 * s), (base_x - 160 * s, base_y - 30 * s), (base_x - 60 * s, base_y - 55 * s),
               (base_x + 70 * s, base_y - 50 * s), (base_x + 170 * s, base_y - 20 * s), (base_x + 250 * s, base_y + 40 * s)],
              fill=colour, outline=INK, width=max(2, int(4 * s)))


def lighthouse(img, x, base_y, s, lit=True, beam=None):
    """A red-and-white lighthouse standing at [x], its foot at [base_y], [s] times its own size."""
    if beam:
        layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        lx, ly = x, base_y - 470 * s
        angle, length, spread = beam
        for i, alpha in reversed(list(enumerate([170, 95, 45]))):
            sp = spread * (1 + i * 0.7)
            p1 = (lx + math.cos(angle - sp) * length, ly + math.sin(angle - sp) * length)
            p2 = (lx + math.cos(angle + sp) * length, ly + math.sin(angle + sp) * length)
            ld.polygon([(lx, ly), p1, p2], fill=(255, 226, 120, alpha))
        img.paste(Image.alpha_composite(img.convert('RGBA'), layer).convert('RGB'))
    d = ImageDraw.Draw(img)
    w_bottom, w_top, h = 120 * s, 76 * s, 380 * s
    top_y = base_y - h
    for i in range(5):
        y0 = base_y - h * i / 5
        y1 = base_y - h * (i + 1) / 5
        wb = w_bottom - (w_bottom - w_top) * i / 5
        wt = w_bottom - (w_bottom - w_top) * (i + 1) / 5
        d.polygon([(x - wb / 2, y0), (x + wb / 2, y0), (x + wt / 2, y1), (x - wt / 2, y1)],
                  fill=(206, 58, 52) if i % 2 == 0 else (246, 240, 228))
    d.polygon([(x - w_bottom / 2, base_y), (x + w_bottom / 2, base_y), (x + w_top / 2, top_y), (x - w_top / 2, top_y)],
              outline=INK, width=max(2, int(5 * s)))
    # The door and a window.
    d.rounded_rectangle([x - 14 * s, base_y - 56 * s, x + 14 * s, base_y], radius=int(12 * s), fill=(70, 50, 44), outline=INK, width=max(2, int(3 * s)))
    d.rectangle([x - 9 * s, base_y - 230 * s, x + 9 * s, base_y - 200 * s], fill=(255, 214, 110) if lit else (60, 70, 90), outline=INK, width=max(2, int(3 * s)))
    # The gallery, the lamp room and its roof.
    d.rectangle([x - 62 * s, top_y - 14 * s, x + 62 * s, top_y], fill=(60, 60, 70), outline=INK, width=max(2, int(4 * s)))
    for k in range(-5, 6):
        d.line([(x + k * 11 * s, top_y - 14 * s), (x + k * 11 * s, top_y - 40 * s)], fill=INK, width=max(1, int(3 * s)))
    d.line([(x - 60 * s, top_y - 40 * s), (x + 60 * s, top_y - 40 * s)], fill=INK, width=max(2, int(4 * s)))
    lamp = (255, 226, 120) if lit else (120, 140, 170)
    if lit:
        glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse([x - 120 * s, top_y - 150 * s, x + 120 * s, top_y + 40 * s], fill=(255, 220, 120, 90))
        glow = glow.filter(ImageFilter.GaussianBlur(28 * s))
        img.paste(Image.alpha_composite(img.convert('RGBA'), glow).convert('RGB'))
        d = ImageDraw.Draw(img)
    d.rectangle([x - 34 * s, top_y - 92 * s, x + 34 * s, top_y - 14 * s], fill=lamp, outline=INK, width=max(2, int(4 * s)))
    d.line([(x, top_y - 92 * s), (x, top_y - 14 * s)], fill=INK, width=max(1, int(3 * s)))
    d.polygon([(x - 46 * s, top_y - 92 * s), (x + 46 * s, top_y - 92 * s), (x, top_y - 140 * s)], fill=(206, 58, 52), outline=INK, width=max(2, int(4 * s)))
    d.ellipse([x - 7 * s, top_y - 152 * s, x + 7 * s, top_y - 138 * s], fill=INK)
    return top_y


def cat(img, x, y, s, colour=(40, 38, 48), facing=1, sleeping=False, eyes=(255, 214, 90)):
    """Pip: a small cat sitting with its feet at ([x], [y]); [facing] 1 looks right, -1 left."""
    d = ImageDraw.Draw(img)
    lw = max(2, int(4 * s))
    if sleeping:
        d.ellipse([x - 70 * s, y - 52 * s, x + 70 * s, y], fill=colour, outline=INK, width=lw)
        d.ellipse([x + facing * 30 * s - 34 * s, y - 70 * s, x + facing * 30 * s + 34 * s, y - 8 * s], fill=colour, outline=INK, width=lw)
        hx = x + facing * 30 * s
        d.polygon([(hx - 30 * s, y - 52 * s), (hx - 22 * s, y - 92 * s), (hx - 4 * s, y - 64 * s)], fill=colour, outline=INK)
        d.polygon([(hx + 30 * s, y - 52 * s), (hx + 22 * s, y - 92 * s), (hx + 4 * s, y - 64 * s)], fill=colour, outline=INK)
        for ex in (-12, 12):
            d.arc([hx + ex * s - 9 * s, y - 46 * s, hx + ex * s + 9 * s, y - 34 * s], 0, 180, fill=(230, 230, 230), width=max(1, int(3 * s)))
        d.arc([x - facing * 110 * s, y - 40 * s, x - facing * 10 * s, y + 20 * s], 180 if facing > 0 else 270, 300 if facing > 0 else 360, fill=INK, width=int(14 * s))
        return
    # Tail, body, head, ears, eyes.
    d.arc([x - facing * 95 * s - 40 * s, y - 120 * s, x - facing * 95 * s + 40 * s, y - 10 * s], 90 if facing > 0 else -90, 300 if facing > 0 else 120, fill=colour, width=int(16 * s))
    d.ellipse([x - 52 * s, y - 120 * s, x + 52 * s, y], fill=colour, outline=INK, width=lw)
    hx, hy = x + facing * 12 * s, y - 146 * s
    d.polygon([(hx - 40 * s, hy - 12 * s), (hx - 34 * s, hy - 70 * s), (hx - 6 * s, hy - 34 * s)], fill=colour, outline=INK, width=lw)
    d.polygon([(hx + 40 * s, hy - 12 * s), (hx + 34 * s, hy - 70 * s), (hx + 6 * s, hy - 34 * s)], fill=colour, outline=INK, width=lw)
    d.ellipse([hx - 46 * s, hy - 44 * s, hx + 46 * s, hy + 40 * s], fill=colour, outline=INK, width=lw)
    for ex in (-17, 17):
        cx, cy = hx + ex * s + facing * 6 * s, hy - 4 * s
        d.ellipse([cx - 10 * s, cy - 12 * s, cx + 10 * s, cy + 12 * s], fill=eyes, outline=INK, width=max(1, int(2 * s)))
        d.ellipse([cx - 3 * s + facing * 3 * s, cy - 8 * s, cx + 3 * s + facing * 3 * s, cy + 8 * s], fill=INK)
    d.polygon([(hx + facing * 6 * s - 5 * s, hy + 14 * s), (hx + facing * 6 * s + 5 * s, hy + 14 * s), (hx + facing * 6 * s, hy + 20 * s)], fill=(236, 140, 150))


def boat(img, x, y, s, tilt=0.0):
    layer = Image.new('RGBA', (int(260 * s), int(300 * s)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, by = 130 * s, 240 * s
    d.polygon([(cx - 110 * s, by - 40 * s), (cx + 110 * s, by - 40 * s), (cx + 80 * s, by), (cx - 80 * s, by)], fill=(150, 92, 54), outline=INK, width=max(2, int(4 * s)))
    d.line([(cx, by - 40 * s), (cx, by - 230 * s)], fill=INK, width=max(2, int(6 * s)))
    d.polygon([(cx + 6 * s, by - 225 * s), (cx + 6 * s, by - 52 * s), (cx + 100 * s, by - 52 * s)], fill=(250, 248, 240), outline=INK, width=max(2, int(4 * s)))
    d.polygon([(cx - 6 * s, by - 190 * s), (cx - 6 * s, by - 52 * s), (cx - 74 * s, by - 52 * s)], fill=(236, 226, 205), outline=INK, width=max(2, int(4 * s)))
    if tilt:
        layer = layer.rotate(tilt, resample=Image.BICUBIC, expand=False)
    img.paste(layer, (int(x - 130 * s), int(y - 240 * s)), layer)


def clouds(img, box, colour=(70, 74, 92), n=7, seed=3):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for _ in range(n):
        cx = rnd.randint(x0 - 40, x1 + 40)
        cy = rnd.randint(y0, y1)
        for k in range(5):
            r = rnd.randint(50, 110)
            ox, oy = rnd.randint(-120, 120), rnd.randint(-30, 30)
            d.ellipse([cx + ox - r, cy + oy - r * 0.7, cx + ox + r, cy + oy + r * 0.7], fill=colour)


def rain(img, box, colour=(180, 196, 220), n=160, seed=5):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
        d.line([(x, y), (x - 14, y + 38)], fill=colour, width=3)


def lightning(img, x, y, s):
    d = ImageDraw.Draw(img)
    pts = [(x, y), (x + 40 * s, y + 120 * s), (x + 10 * s, y + 120 * s), (x + 60 * s, y + 260 * s), (x - 10 * s, y + 140 * s), (x + 20 * s, y + 140 * s)]
    d.polygon(pts, fill=(255, 246, 170), outline=(255, 255, 255))


def wrap(text, font, width):
    words, lines, line = text.split(), [], ''
    for w in words:
        t = (line + ' ' + w).strip()
        if font.getlength(t) <= width:
            line = t
        else:
            lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def balloon(img, text, cx, cy, tail, width=380, font=TALK):
    """A speech balloon centred on ([cx], [cy]), its tail pointing at [tail]."""
    d = ImageDraw.Draw(img)
    lines = wrap(text, font, width)
    lh = font.size * 1.25
    tw = max(font.getlength(l) for l in lines)
    th = lh * len(lines)
    rx, ry = tw / 2 + 46, th / 2 + 34
    tx, ty = tail
    ang = math.atan2(ty - cy, tx - cx)
    base = 0.22
    p1 = (cx + math.cos(ang - base) * rx * 0.8, cy + math.sin(ang - base) * ry * 0.8)
    p2 = (cx + math.cos(ang + base) * rx * 0.8, cy + math.sin(ang + base) * ry * 0.8)
    d.polygon([p1, (tx, ty), p2], fill=(255, 255, 255), outline=INK, width=5)
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=(255, 255, 255), outline=INK, width=5)
    d.polygon([p1, (tx, ty), p2], fill=(255, 255, 255))
    d.line([p1, (tx, ty), p2], fill=INK, width=5)
    y = cy - th / 2
    for l in lines:
        d.text((cx - font.getlength(l) / 2, y), l, font=font, fill=INK)
        y += lh


def caption(img, text, x, y, width, font=CAPTION):
    d = ImageDraw.Draw(img)
    lines = wrap(text, font, width - 40)
    lh = font.size * 1.3
    h = lh * len(lines) + 30
    d.rectangle([x, y, x + width, y + h], fill=(255, 238, 168), outline=INK, width=5)
    yy = y + 15
    for l in lines:
        d.text((x + 20, yy), l, font=font, fill=INK)
        yy += lh
    return y + h


def sfx(img, text, x, y, angle=-8, fill=(255, 214, 60), font=SFX):
    w = int(font.getlength(text)) + 40
    layer = Image.new('RGBA', (w, font.size + 60), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((20, 10), text, font=font, fill=fill, stroke_width=8, stroke_fill=INK)
    layer = layer.rotate(angle, resample=Image.BICUBIC, expand=True)
    img.paste(layer, (int(x), int(y)), layer)


def frame(img, box):
    ImageDraw.Draw(img).rectangle(box, outline=INK, width=BORDER)


def panel(page, box, draw):
    """Draws a panel: [draw] gets an image the panel's size."""
    x0, y0, x1, y1 = box
    p = Image.new('RGB', (x1 - x0, y1 - y0), (255, 255, 255))
    draw(p)
    page.paste(p, (x0, y0))
    frame(page, box)


def cover():
    img = Image.new('RGB', (W, H))
    sky = (18, 26, 58)
    gradient(img, (0, 0, W, H), (10, 16, 44), (44, 58, 104))
    stars(img, (0, 0, W, 900), 110)
    moon(img, 860, 230, 70, (16, 24, 56))
    sea(img, (0, 1060, W, H), 1100, (24, 64, 96), (60, 110, 150), rough=1.2)
    rocks(img, 560, 1150, 1.3)
    top = lighthouse(img, 560, 1110, 1.15, lit=True, beam=(math.pi * 1.06, 1100, 0.09))
    cat(img, 622, top - 14 * 1.15 + 2, 0.42, facing=1)
    d = ImageDraw.Draw(img)
    for i, line in enumerate(['THE', 'LIGHTHOUSE', 'CAT']):
        f = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 120 if i != 1 else 168)
        x = (W - f.getlength(line)) / 2
        d.text((x, 70 + [0, 100, 270][i]), line, font=f, fill=(255, 220, 92), stroke_width=9, stroke_fill=INK)
    small = ImageFont.truetype(str(FONTS / 'poppins_medium.ttf'), 34)
    t = 'A KoLib sample comic'
    d.text(((W - small.getlength(t)) / 2, H - 90), t, font=small, fill=(240, 236, 220))
    return img


def page1():
    img = Image.new('RGB', (W, H), (255, 255, 255))
    top = (GUTTER, GUTTER, W - GUTTER, 760)

    def dusk(p):
        gradient(p, (0, 0, p.width, p.height), (250, 170, 120), (255, 222, 170))
        sea(p, (0, 520, p.width, p.height), 540, (40, 104, 140), (90, 150, 180), phase=1.2)
        rocks(p, 700, 590, 0.85)
        lighthouse(p, 700, 560, 0.82, lit=False)
        cat(p, 750, 560 - 380 * 0.82 - 14 * 0.82 + 2, 0.27, facing=-1)
        caption(p, 'On a rock at the edge of the sea stood a lighthouse. In it lived Pip.', 24, 24, 560)

    def stairs(p):
        gradient(p, (0, 0, p.width, p.height), (120, 90, 80), (70, 52, 50))
        d = ImageDraw.Draw(p)
        for i in range(14):
            y = p.height - 40 - i * 46
            x = 40 + (i % 2) * 120 + i * 18
            d.polygon([(x, y), (x + 200, y), (x + 230, y - 30), (x + 30, y - 30)], fill=(196, 160, 120), outline=INK, width=4)
        cat(p, 300, p.height - 40 - 7 * 46 - 30, 0.55, facing=1)
        caption(p, 'Every evening, Pip climbed all ninety-nine steps.', 20, 20, p.width - 40)

    def lamp(p):
        gradient(p, (0, 0, p.width, p.height), (30, 36, 70), (60, 66, 110))
        d = ImageDraw.Draw(p)
        cx, cy = p.width / 2 + 40, p.height / 2 + 30
        glow = Image.new('RGBA', p.size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse([cx - 230, cy - 230, cx + 230, cy + 230], fill=(255, 220, 120, 140))
        glow = glow.filter(ImageFilter.GaussianBlur(40))
        p.paste(Image.alpha_composite(p.convert('RGBA'), glow).convert('RGB'))
        d = ImageDraw.Draw(p)
        d.ellipse([cx - 110, cy - 110, cx + 110, cy + 110], fill=(255, 236, 150), outline=INK, width=6)
        d.ellipse([cx - 60, cy - 60, cx + 60, cy + 60], fill=(255, 250, 220))
        cat(p, 120, p.height - 30, 0.6, facing=1)
        balloon(p, 'Lamp on!', 170, 150, (130, p.height - 230), width=200)

    panel(img, top, dusk)
    half = (W - 3 * GUTTER) // 2
    panel(img, (GUTTER, 760 + GUTTER, GUTTER + half, H - GUTTER), stairs)
    panel(img, (2 * GUTTER + half, 760 + GUTTER, W - GUTTER, H - GUTTER), lamp)
    return img


def page2():
    img = Image.new('RGB', (W, H), (255, 255, 255))

    def night(p):
        gradient(p, (0, 0, p.width, p.height), (16, 22, 52), (42, 54, 96))
        stars(p, (0, 0, p.width, p.height // 2), 30)
        clouds(p, (p.width // 2, 40, p.width + 100, 220), colour=(64, 70, 96))
        sea(p, (0, p.height - 140, p.width, p.height), p.height - 120, (22, 58, 90), (60, 100, 140))
        caption(p, 'One night, a storm rolled in from the sea.', 24, 24, 560)

    def storm(p):
        gradient(p, (0, 0, p.width, p.height), (34, 36, 54), (70, 76, 100))
        clouds(p, (0, -40, p.width, 200), colour=(52, 54, 72), n=9)
        lightning(p, p.width - 260, 60, 1.3)
        rain(p, (0, 0, p.width, p.height))
        sea(p, (0, 300, p.width, p.height), 330, (26, 56, 84), (90, 130, 170), rough=2.6, phase=0.4)
        boat(p, p.width / 2 - 60, 380, 1.05, tilt=14)
        sfx(p, 'CRASH!', 40, 60, angle=10)

    def worried(p):
        gradient(p, (0, 0, p.width, p.height), (40, 44, 70), (70, 74, 104))
        rain(p, (0, 0, p.width, p.height), n=80)
        d = ImageDraw.Draw(p)
        d.rectangle([0, p.height - 120, p.width, p.height - 90], fill=(60, 60, 70), outline=INK, width=5)
        for k in range(0, p.width, 46):
            d.line([(k, p.height - 120), (k, p.height - 210)], fill=INK, width=5)
        d.line([(0, p.height - 210), (p.width, p.height - 210)], fill=INK, width=6)
        cat(p, p.width - 180, p.height - 120, 0.85, facing=-1)
        balloon(p, "A boat! They can't see the rocks!", 300, 150, (p.width - 230, p.height - 330), width=360)

    panel(img, (GUTTER, GUTTER, W - GUTTER, 420), night)
    panel(img, (GUTTER, 420 + GUTTER, W - GUTTER, 960), storm)
    panel(img, (GUTTER, 960 + GUTTER, W - GUTTER, H - GUTTER), worried)
    return img


def spread():
    """The wide page across two: the beam finds the boat."""
    SW = 2 * W
    img = Image.new('RGB', (SW, H), (255, 255, 255))

    def scene(p):
        gradient(p, (0, 0, p.width, p.height), (22, 26, 48), (56, 64, 96))
        clouds(p, (0, -60, p.width, 260), colour=(48, 52, 72), n=12, seed=11)
        rain(p, (0, 0, p.width, p.height), n=260, seed=9)
        sea(p, (0, 900, p.width, p.height), 940, (24, 58, 88), (96, 140, 180), rough=2.2)
        rocks(p, 380, 1010, 1.3)
        top = lighthouse(p, 380, 970, 1.2, lit=True, beam=(-0.05, 1900, 0.055))
        cat(p, 446, top - 14 * 1.2 + 2, 0.44, facing=1)
        rocks(p, 1500, 1010, 0.9, colour=(46, 44, 52))
        boat(p, 1700, 960, 1.4, tilt=-8)
        balloon(p, 'Brighter, lamp! Brighter!', 700, 210, (470, top - 90), width=330)
        balloon(p, 'Thank you, lighthouse! We see the rocks!', 1640, 360, (1700, 760), width=420)
        sfx(p, 'WHOOSH', 900, 520, angle=-6, fill=(255, 236, 150))

    panel(img, (GUTTER, GUTTER, SW - GUTTER, H - GUTTER), scene)
    return img


def page4():
    img = Image.new('RGB', (W, H), (255, 255, 255))

    def morning(p):
        gradient(p, (0, 0, p.width, p.height), (255, 214, 150), (190, 226, 240))
        d = ImageDraw.Draw(p)
        d.ellipse([p.width - 300, 120, p.width - 140, 280], fill=(255, 246, 200))
        sea(p, (0, 430, p.width, p.height), 450, (60, 140, 170), (130, 190, 210), rough=0.6)
        boat(p, p.width - 260, 470, 0.7)
        rocks(p, 220, 470, 0.9)
        lighthouse(p, 220, 440, 0.95, lit=False)
        caption(p, 'By morning the storm had gone, and so had the boat: safely home.', 24, 24, 600)

    def asleep(p):
        gradient(p, (0, 0, p.width, p.height), (255, 236, 196), (255, 214, 160))
        d = ImageDraw.Draw(p)
        d.rectangle([0, p.height - 170, p.width, p.height], fill=(110, 110, 120), outline=INK, width=5)
        for k in range(0, p.width, 52):
            d.line([(k, p.height - 170), (k, p.height - 300)], fill=INK, width=6)
        d.line([(0, p.height - 300), (p.width, p.height - 300)], fill=INK, width=7)
        cat(p, p.width / 2, p.height - 170, 1.3, facing=1, sleeping=True)
        z = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 70)
        for i, (zx, zy) in enumerate([(p.width / 2 + 120, p.height - 330), (p.width / 2 + 180, p.height - 420), (p.width / 2 + 250, p.height - 520)]):
            d.text((zx, zy), 'z', font=z, fill=INK)
        caption(p, 'And Pip slept until teatime.', 24, 24, 460)
        f = ImageFont.truetype(str(FONTS / 'bangers.ttf'), 96)
        t = 'THE END'
        d.text(((p.width - f.getlength(t)) / 2, 150), t, font=f, fill=(206, 58, 52), stroke_width=6, stroke_fill=INK)

    panel(img, (GUTTER, GUTTER, W - GUTTER, 720), morning)
    panel(img, (GUTTER, 720 + GUTTER, W - GUTTER, H - GUTTER), asleep)
    return img


COMIC_INFO = """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <Title>The Lighthouse Cat</Title>
  <Series>The Lighthouse Cat</Series>
  <Number>1</Number>
  <Count>1</Count>
  <Summary>On a stormy night, Pip the lighthouse cat keeps the lamp burning for a boat lost at sea. A short comic made for KoLib, to try the comic reader with.</Summary>
  <Year>2026</Year>
  <Writer>KoLib</Writer>
  <Penciller>KoLib</Penciller>
  <Genre>Children's, Adventure</Genre>
  <LanguageISO>en</LanguageISO>
  <Manga>No</Manga>
</ComicInfo>
"""

if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    pages = [('00.jpg', cover()), ('01.jpg', page1()), ('02.jpg', page2()), ('03.jpg', spread()), ('04.jpg', page4())]
    for name, im in pages:
        im.save(OUT / name, quality=86, optimize=True)
    cbz = HERE / 'build' / 'lighthouse-cat.cbz'
    with zipfile.ZipFile(cbz, 'w', zipfile.ZIP_STORED) as z:
        for name, _ in pages:
            z.write(OUT / name, name)
        z.writestr('ComicInfo.xml', COMIC_INFO)
    print(cbz, cbz.stat().st_size)
