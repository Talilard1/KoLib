#!/usr/bin/env python3
"""
Makes the pictures of pages the text recognition tests read, and Tesseract's hOCR for them:
app/src/test/resources/ocr/page1..4.png and .hocr. The text is from Lewis Carroll's "Alice's
Adventures in Wonderland" (1865) and a few Greek words, set out like a printed book: a chapter's
first page, a page with a running head, two columns like a dictionary's, and a page that's a picture.

Needs Pillow, Tesseract, and its eng and grc data:
    python3 tools/ocr/make_fixtures.py /path/to/tessdata
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "test", "resources", "ocr")
W, H = 1700, 2200  # a 5.7 x 7.3 inch page at 300 dpi
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else SERIF, size)


def wrap(draw, text, f, width, indent=0):
    """Lines of [text] no wider than [width], the first [indent] in."""
    words = text.split()
    lines, line = [], ""
    room = width - indent
    for w in words:
        trial = (line + " " + w).strip()
        if draw.textlength(trial, font=f) <= room:
            line = trial
        else:
            lines.append(line)
            line = w
            room = width
    lines.append(line)
    return lines


def paragraph(draw, text, f, x, y, width, indent=40, gap=50, hyphenate=None):
    lines = wrap(draw, text, f, width, indent)
    if hyphenate:
        # Break a word over two lines with a hyphen, as printers do.
        first, rest = hyphenate
        for i, l in enumerate(lines):
            if l.endswith(first + rest) or (first + rest) in l.split():
                break
    for i, l in enumerate(lines):
        draw.text((x + (indent if i == 0 else 0), y), l, font=f, fill=0)
        y += gap
    return y


def page1():
    im = Image.new("L", (W, H), 255)
    d = ImageDraw.Draw(im)
    big = font(64, bold=True)
    mid = font(48)
    body = font(34)
    title = "CHAPTER I."
    d.text(((W - d.textlength(title, font=big)) / 2, 300), title, font=big, fill=0)
    sub = "Down the Rabbit-Hole"
    d.text(((W - d.textlength(sub, font=mid)) / 2, 420), sub, font=mid, fill=0)
    y = 600
    y = paragraph(d, "Alice was beginning to get very tired of sitting by her sister on the bank, and of having "
                     "nothing to do: once or twice she had peeped into the book her sister was reading, but it had "
                     "no pictures or conversations in it, “and what is the use of a book,” thought Alice "
                     "“without pictures or conversations?”", body, 200, y, 1300) + 10
    # Carries on over the page: no full stop at the end.
    lines = ["So she was considering in her own mind (as well as she could, for the hot",
             "day made her feel very sleepy and stupid), whether the pleasure of making a",
             "daisy-chain would be worth the trouble of getting up and picking the daisies,",
             "when suddenly a White Rabbit with pink eyes ran close by her. There was noth-",
             "ing so very remarkable in that; nor did Alice think it so very much out of the"]
    for i, l in enumerate(lines):
        d.text((200 + (40 if i == 0 else 0), y), l, font=body, fill=0)
        y += 50
    d.text(((W - d.textlength("1", font=body)) / 2, 2050), "1", font=body, fill=0)
    return im


def page2():
    im = Image.new("L", (W, H), 255)
    d = ImageDraw.Draw(im)
    body = font(34)
    head = font(28)
    d.text((200, 120), "2", font=head, fill=0)
    t = "ALICE’S ADVENTURES IN WONDERLAND"
    d.text(((W - d.textlength(t, font=head)) / 2, 120), t, font=head, fill=0)
    y = 260
    lines = ["way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!”",
             "(when she thought it over afterwards, it occurred to her that she ought to",
             "have wondered at this, but at the time it all seemed quite natural)."]
    for l in lines:
        d.text((200, y), l, font=body, fill=0)
        y += 50
    y += 10
    y = paragraph(d, "In another moment down went Alice after it, never once considering how in the world she "
                     "was to get out again. The rabbit-hole went straight on like a tunnel for some way, and then "
                     "dipped suddenly down, so suddenly that Alice had not a moment to think about stopping "
                     "herself before she found herself falling down a very deep well.", body, 200, y, 1300) + 10
    y = paragraph(d, "Either the well was very deep, or she fell very slowly, for she had plenty of time as she "
                     "went down to look about her and to wonder what was going to happen next.", body, 200, y, 1300)
    return im


def page3():
    im = Image.new("L", (W, H), 255)
    d = ImageDraw.Draw(im)
    body = font(30)
    bold = font(30, bold=True)
    head = font(30)
    t = "ἀγαθός — ἄγω"
    d.text(((W - d.textlength(t, font=head)) / 2, 110), t, font=head, fill=0)
    d.text((1450, 110), "3", font=head, fill=0)
    entries = [
        ("ἀγαθός,", "good, brave, noble, in reference to birth, as in Homer; of things, serviceable, good for a purpose."),
        ("ἀγαλμα,", "a glory, delight, honour; then a pleasing gift, especially one for the gods."),
        ("ἄγαν,", "very, much, very much; in a bad sense, too much, as in the famous saying."),
        ("ἄγγελος,", "a messenger, envoy, one that announces or tells."),
        ("ἄγω,", "to lead, to carry, to bring; of persons, to lead or take with one."),
        ("ἀγών,", "a gathering, an assembly met to see games; a contest for a prize."),
    ]
    col_w = 600
    xs = [150, 150 + col_w + 100]
    y = [240, 240]
    for i, (word, gloss) in enumerate(entries):
        c = 0 if i < 3 else 1
        x = xs[c]
        first = word + " "
        d.text((x, y[c]), word, font=bold, fill=0)
        indent = d.textlength(first, font=bold)
        lines = wrap(d, gloss, body, col_w, indent)
        for j, l in enumerate(lines):
            d.text((x + (indent if j == 0 else 30), y[c]), l, font=body, fill=0)
            y[c] += 44
        y[c] += 16
    return im


def page4():
    im = Image.new("L", (W, H), 255)
    d = ImageDraw.Draw(im)
    # A drawing: shapes and shading, no words.
    d.rectangle((250, 300, 1450, 1700), outline=0, width=6)
    for k in range(0, 1400, 18):
        d.line((250, 300 + k, 250 + k * 0.85, 1700), fill=80, width=2)
    d.ellipse((600, 600, 1100, 1100), outline=0, width=10, fill=170)
    d.polygon([(900, 1200), (1300, 1600), (500, 1600)], outline=0, fill=110)
    return im


def main():
    tessdata = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(OUT, exist_ok=True)
    for n, make in enumerate([page1, page2, page3, page4], start=1):
        png = os.path.join(OUT, "page%d.png" % n)
        make().save(png, dpi=(300, 300))
        base = os.path.join(OUT, "page%d" % n)
        cmd = ["tesseract", png, base, "-l", "eng+grc", "--psm", "3", "--dpi", "300", "-c", "tessedit_create_hocr=1"]
        if tessdata:
            cmd[3:3] = ["--tessdata-dir", tessdata]
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(base + ".hocr")


if __name__ == "__main__":
    main()
