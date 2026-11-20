"""Builds the sample EPUBs that come with KoLib from public-domain texts (Carroll, Austen, Chesterton)."""
import html, re, sys, uuid, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
# The texts: the folder of NLTK's Gutenberg corpus (carroll-alice.txt and the rest), given as the first argument.
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / 'build' / 'gutenberg'
OUT = HERE / 'build' / 'epub'
COVERS = HERE / 'build' / 'covers'

BOOKS = [
    dict(key='alice', file='carroll-alice.txt', title="Alice's Adventures in Wonderland", author='Lewis Carroll',
         file_as='Carroll, Lewis', year='1865', tags=['Classics', 'Fantasy', "Children's"],
         description="Alice follows a white rabbit down a hole and into a world where nothing behaves as it should: "
                     "a caterpillar on a mushroom, a mad tea party and a queen who wants everyone's head.",
         heading=r'^CHAPTER ([IVXLC]+)\. (.+)$', number='Chapter {0}'),
    dict(key='persuasion', file='austen-persuasion.txt', title='Persuasion', author='Jane Austen',
         file_as='Austen, Jane', year='1817', tags=['Classics', 'Romance', 'Regency'],
         description="Eight years after she was talked out of marrying him, Anne Elliot meets Captain Wentworth again. "
                     "Austen's last finished novel, about second chances.",
         heading=r'^Chapter (\d+)$', number='Chapter {0}'),
    dict(key='brown', file='chesterton-brown.txt', title='The Wisdom of Father Brown', author='G. K. Chesterton',
         file_as='Chesterton, G. K.', year='1914', tags=['Classics', 'Mystery', 'Short stories'],
         description="Twelve mysteries solved by Father Brown, a small, untidy priest who understands crime because "
                     "he understands people.",
         heading=r'^([IVXLC]+)\. (.+)$', number='{0}'),
]


def smart(t):
    """Typographic quotes and dashes, from a plain-text book's straight ones."""
    t = t.replace('--', '—')
    t = re.sub(r'(^|[\s(\[—])"', r'\1“', t)
    t = t.replace('"', '”')
    t = re.sub(r"(^|[\s(\[—“])'", r'\1‘', t)
    t = t.replace("'", '’')
    return t


def para(block):
    lines = block.split('\n')
    verse = len(lines) > 1 and all(l.startswith('  ') or not l.strip() for l in lines)
    if verse:
        inner = '<br/>\n'.join(html.escape(smart(l.strip()), quote=False) for l in lines if l.strip())
        return f'<p class="verse">{inner}</p>'
    text = ' '.join(l.strip() for l in lines if l.strip())
    if not text:
        return ''
    if text.strip('* ') == '':
        return '<p class="break">* * *</p>'
    return f'<p>{html.escape(smart(text), quote=False)}</p>'


def chapters(book):
    raw = (SRC / book['file']).read_text(encoding='utf-8', errors='replace').replace('\r\n', '\n')
    lines = raw.split('\n')
    if lines and lines[0].startswith('['):
        lines = lines[1:]
    head = re.compile(book['heading'])
    out, cur = [], None
    alone = re.compile(r'^\s*CHAPTER ([IVXLC]+)\.?\s*$')
    want_title = False
    for line in lines:
        m = head.match(line.strip())
        if m and not line.startswith(' '):
            cur = dict(number=book['number'].format(m.group(1)), title=smart(m.group(2)) if m.lastindex and m.lastindex >= 2 else '', body=[])
            out.append(cur)
            continue
        a = alone.match(line)
        if a and book['key'] == 'alice':
            # The last chapter's heading: its number centred on one line, its name on the next.
            cur = dict(number=book['number'].format(a.group(1)), title='', body=[])
            out.append(cur)
            want_title = True
            continue
        if want_title and line.strip():
            cur['title'] = smart(line.strip())
            want_title = False
            continue
        if cur is not None:
            cur['body'].append(line)
    for c in out:
        text = '\n'.join(c['body']).strip('\n')
        blocks = re.split(r'\n\s*\n', text)
        ps = [para(b.rstrip()) for b in blocks]
        ps = [p for p in ps if p and p not in ('<p>Finis</p>', '<p>THE END</p>')]
        c['html'] = '\n'.join(ps)
    return out


CSS = """body { margin: 0; }
h1 { text-align: center; font-size: 1.2em; font-weight: normal; letter-spacing: 0.12em; text-transform: uppercase; margin: 2.5em 0 0.4em; }
h2 { text-align: center; font-size: 1.5em; font-style: italic; font-weight: normal; margin: 0 0 1.8em; }
p { margin: 0; text-indent: 1.4em; text-align: justify; }
h1 + p, h2 + p, p.break + p, p.verse + p { text-indent: 0; }
p.verse { text-indent: 0; margin: 0.8em 0 0.8em 2em; text-align: left; }
p.break { text-indent: 0; text-align: center; margin: 1em 0; }
div.cover { text-align: center; margin: 0; padding: 0; }
div.cover img { max-width: 100%; max-height: 100%; }
p.note { text-indent: 0; text-align: center; margin-top: 2em; font-size: 0.9em; }
"""


def page(title, body, lang='en'):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="{lang}" xml:lang="{lang}">
<head>
<meta charset="utf-8"/>
<title>{html.escape(title)}</title>
<link rel="stylesheet" type="text/css" href="../Styles/style.css"/>
</head>
<body>
{body}
</body>
</html>
"""


def build(book):
    chs = chapters(book)
    ident = 'urn:uuid:' + str(uuid.uuid5(uuid.NAMESPACE_URL, 'kolib-sample:' + book['key']))
    cover = COVERS / f"{book['key']}.jpg"
    files = {}
    files['OEBPS/Styles/style.css'] = CSS
    spine, toc = [], []
    if cover.exists():
        files['OEBPS/Images/cover.jpg'] = cover.read_bytes()
        files['OEBPS/Text/cover.xhtml'] = page(book['title'], '<div class="cover"><img src="../Images/cover.jpg" alt="' + html.escape(book['title']) + '"/></div>')
        spine.append(('cover', 'Text/cover.xhtml', 'no'))
    title_body = (f'<h2 style="margin-top:30%">{html.escape(book["title"])}</h2>\n'
                  f'<p class="note">{html.escape(book["author"])}</p>\n'
                  f'<p class="note">First published in {book["year"]}. This book is in the public domain.</p>')
    files['OEBPS/Text/title.xhtml'] = page(book['title'], title_body)
    spine.append(('title', 'Text/title.xhtml', 'yes'))
    for i, c in enumerate(chs, 1):
        name = f'Text/ch{i:02d}.xhtml'
        heading = f'<h1>{html.escape(c["number"])}</h1>' + (f'\n<h2>{html.escape(c["title"])}</h2>' if c['title'] else '')
        files['OEBPS/' + name] = page(c['number'], f'<section epub:type="chapter">\n{heading}\n{c["html"]}\n</section>')
        spine.append((f'ch{i:02d}', name, 'yes'))
        toc.append((name, c['number'] + (': ' + c['title'] if c['title'] else '')))
    nav_items = '\n'.join(f'<li><a href="{h}">{html.escape(t)}</a></li>' for h, t in toc)
    files['OEBPS/nav.xhtml'] = f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en" xml:lang="en">
<head><meta charset="utf-8"/><title>Contents</title></head>
<body>
<nav epub:type="toc" id="toc"><h1>Contents</h1><ol>
{nav_items}
</ol></nav>
</body>
</html>
"""
    ncx_points = '\n'.join(
        f'<navPoint id="np{i}" playOrder="{i}"><navLabel><text>{html.escape(t)}</text></navLabel><content src="{h}"/></navPoint>'
        for i, (h, t) in enumerate(toc, 1))
    files['OEBPS/toc.ncx'] = f"""<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="{ident}"/></head>
<docTitle><text>{html.escape(book['title'])}</text></docTitle>
<navMap>
{ncx_points}
</navMap>
</ncx>
"""
    manifest = ['<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>',
                '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>',
                '<item id="css" href="Styles/style.css" media-type="text/css"/>']
    if cover.exists():
        manifest.append('<item id="cover-image" href="Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>')
    for sid, href, _ in spine:
        manifest.append(f'<item id="{sid}" href="{href}" media-type="application/xhtml+xml"/>')
    spine_xml = '\n'.join(f'<itemref idref="{sid}" linear="{lin}"/>' for sid, _, lin in spine)
    subjects = '\n'.join(f'<dc:subject>{html.escape(t)}</dc:subject>' for t in book['tags'])
    files['OEBPS/content.opf'] = f"""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
<dc:identifier id="bookid">{ident}</dc:identifier>
<dc:title>{html.escape(book['title'])}</dc:title>
<dc:creator id="author">{html.escape(book['author'])}</dc:creator>
<meta refines="#author" property="file-as">{html.escape(book['file_as'])}</meta>
<meta refines="#author" property="role" scheme="marc:relators">aut</meta>
<dc:language>en</dc:language>
<dc:date>{book['year']}</dc:date>
<dc:rights>Public domain</dc:rights>
<dc:description>{html.escape(book['description'])}</dc:description>
{subjects}
<meta property="dcterms:modified">2026-11-21T00:00:00Z</meta>
{'<meta name="cover" content="cover-image"/>' if cover.exists() else ''}
</metadata>
<manifest>
{chr(10).join(manifest)}
</manifest>
<spine toc="ncx">
{spine_xml}
</spine>
</package>
"""
    files['META-INF/container.xml'] = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{book['key']}.epub"
    with zipfile.ZipFile(out, 'w') as z:
        z.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
        for name, data in files.items():
            info = zipfile.ZipInfo(name, date_time=(2026, 11, 21, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data if isinstance(data, bytes) else data.encode('utf-8'))
    print(out, len(chs), 'chapters', out.stat().st_size, 'bytes')


if __name__ == '__main__':
    for b in BOOKS:
        build(b)
