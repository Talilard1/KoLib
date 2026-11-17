"""Checks of the text editor's page script (app/src/main/assets/editor/editor.js) in a real browser engine.

The app runs the script in Android's WebView, which tests on a computer can't start; this runs the same script in
Chromium (the same engine) over a small chapter, and checks pasting, the cursor, inserting, alignment, selecting and
copying. Needs Python with Playwright and its Chromium:  python3 tools/editor-tests/test_editor.py
"""
import functools, http.server, json, os, shutil, sys, tempfile, threading
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
EDITOR = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'editor')

def serve():
    site = tempfile.mkdtemp()
    for name in ('editor.js', 'editor.css'): shutil.copy(os.path.join(EDITOR, name), site)
    shutil.copy(os.path.join(HERE, 'chapter.xhtml'), site)
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args): pass
    handler = functools.partial(Quiet, directory=site)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, site

CARET = """() => { const s = getSelection(); const r = s.getRangeAt(0);
  const pre = document.createRange(); pre.selectNodeContents(document.body); pre.setEnd(r.startContainer, r.startOffset);
  const post = document.createRange(); post.selectNodeContents(document.body); post.setStart(r.endContainer, r.endOffset);
  return [pre.toString().slice(-14), post.toString().slice(0, 10)]; }"""
SET = """([id, off]) => { const el = document.getElementById(id); let n = el.firstChild; while (n && n.nodeType !== 3) n = n.firstChild || n.nextSibling;
  const r = document.createRange(); r.setStart(n, off); r.collapse(true); const s = getSelection(); s.removeAllRanges(); s.addRange(r); document.body.focus(); }"""
PASTE = """([t, h]) => { const dt = new DataTransfer(); dt.setData('text/plain', t); if (h) dt.setData('text/html', h);
  document.body.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true })); }"""
BODY = "() => document.body.innerHTML.replace(/\\s*xmlns(:\\w+)?=\"[^\"]*\"/g, '').replace(/\\s+/g, ' ')"
VALID = """() => { KLED.flush(); const d = new DOMParser().parseFromString('<body xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">' + document.body.innerHTML + '</body>', 'application/xhtml+xml');
  return !d.querySelector('parsererror') && !/[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]/.test(document.body.innerHTML); }"""
IDS = "() => Array.from(document.body.querySelectorAll('[id]')).map(e => e.id).join(',')"

failed = []
def check(name, got, want):
    ok = got == want if not callable(want) else want(got)
    print(('ok   ' if ok else 'FAIL ') + name + ('' if ok else f'\n       got  {got!r}\n       want {want!r}'))
    if not ok: failed.append(name)

def main():
    server, site = serve()
    url = f'http://127.0.0.1:{server.server_address[1]}/chapter.xhtml'
    with sync_playwright() as p:
        b = p.chromium.launch()
        def fresh():
            pg = b.new_page(viewport={'width': 900, 'height': 900})
            pg.on('pageerror', lambda e: failed.append(f'page error: {e}'))
            pg.goto(url); pg.wait_for_function('!!window.KLED'); return pg
        def settle(pg): pg.wait_for_timeout(900)      # past the pause after which the page tidies and sends

        pg = fresh(); pg.evaluate(SET, ['a', 9]); pg.evaluate("KLED.cmd('scenebreak')")
        check('a scene break goes after the paragraph, inside its section',
              pg.evaluate("() => { const e = document.querySelector('.scene-break'); return [e.parentNode.localName, e.previousElementSibling.id, e.nextElementSibling.id]; }"), ['section', 'a', 'b'])
        pg = fresh(); pg.evaluate(SET, ['li1', 4]); pg.evaluate("KLED.cmd('scenebreak')")
        check('in a list, it goes after the list',
              pg.evaluate("() => { const e = document.querySelector('.scene-break'); return [e.parentNode.localName, e.previousElementSibling.localName, e.nextElementSibling.id]; }"), ['section', 'ul', 'd'])

        pg = fresh(); pg.evaluate(SET, ['b', 3]); pg.evaluate(PASTE, ['ONE first.\n\nTWO second.\n\nTHREE third.', '']); settle(pg)
        check('paragraphs pasted mid-paragraph: the cursor ends after the last', pg.evaluate(CARET), ['d.THREE third.', 'vo italic '])
        check('the split paragraph keeps its id once, on its first half', [pg.evaluate(IDS), '<p id="b">BraONE first.</p><p>TWO second.</p><p>THREE third.vo' in pg.evaluate(BODY)], ['ch1,a,b,c,li1,d', True])
        check('what goes back is well-formed', pg.evaluate(VALID), True)

        pg = fresh(); pg.evaluate(SET, ['b', 3]); pg.evaluate(PASTE, ['ONE first.\n\nTWO second.\n', '']); settle(pg)
        check('whole paragraphs (a break at the end): the words after start their own, with the cursor before them',
              ['<p id="b">BraONE first.</p><p>TWO second.</p><p>vo <i>' in pg.evaluate(BODY), pg.evaluate(CARET)[1]], [True, 'vo italic '])
        pg = fresh(); pg.evaluate(SET, ['d', 0]); pg.evaluate(PASTE, ['ONE first.\nTWO second.\n', ''])
        check('lines with no blank line between are paragraphs too', '<p>ONE first.</p><p>TWO second.</p><p id="d">Delta' in pg.evaluate(BODY), True)
        pg = fresh(); pg.evaluate(SET, ['d', 0]); pg.evaluate(PASTE, ['a line of prose cut to the width of an old screen and\nrunning on to the next line without any break in it,\nas text copied from a PDF often is', ''])
        check('lines cut to a width are one paragraph', 'screen and running on to the next line without any break in it, as text' in pg.evaluate(BODY), True)

        pg = fresh(); pg.evaluate(SET, ['d', 5]); pg.evaluate(PASTE, ['bad \x0b char.\n\nsecond', '']); settle(pg)
        check("characters XML can't hold are left out", pg.evaluate(VALID), True)

        pg = fresh(); pg.evaluate(SET, ['d', 5])
        pg.evaluate(PASTE, ['x', '<meta charset="utf-8"><b style="font-weight:normal" id="docs-x"><p class="c1" style="color:red">One <i>it</i> and <span style="font-weight:700">bo</span></p><h2 id="q">Head</h2><div>Two<br>line<br><br>Three &amp; <script>bad()</script><img src="x.png"/>end</div></b>'])
        settle(pg)
        check('copied markup comes in clean: paragraphs, headings, italics and bold, and nothing else',
              '<p id="d">DeltaOne <i>it</i> and <b>bo</b></p><h2>Head</h2><p>Two<br />line</p><p>Three &amp; end eleven twelve.</p>' in pg.evaluate(BODY), True)

        pg = fresh(); pg.evaluate("() => { const sp = document.querySelector('#c span'); getSelection().collapse(sp.firstChild, 5); document.body.focus(); }")
        pg.keyboard.press('Enter'); pg.keyboard.type('Q'); settle(pg)
        check("the book's own styled span survives being split",
              '<span style="font-variant: small-caps;">small</span></p><p><span style="font-variant: small-caps;">Q caps here</span>' in pg.evaluate(BODY), True)

        pg = fresh()
        pg.evaluate("""() => { const t = document.getElementById('d').firstChild; const sp = document.createElementNS('http://www.w3.org/1999/xhtml', 'span'); sp.setAttribute('style', 'font-size: 16px;');
          const r = document.createRange(); r.setStart(t, 2); r.setEnd(t, 12); r.surroundContents(sp); getSelection().collapse(sp.firstChild, 4); document.body.focus(); KLED.flush(); }""")
        check('a span the browser made is taken out with the cursor left where it was',
              [pg.evaluate("() => document.getElementById('d').innerHTML"), pg.evaluate(CARET)], ['Delta eleven twelve.', [pg.evaluate(CARET)[0][:-6] + 'Delta ', 'eleven twe']])

        pg = fresh(); pg.evaluate(SET, ['b', 3]); pg.evaluate("KLED.cmd('align', 'center')"); pg.wait_for_timeout(200)
        check('centring a paragraph', [pg.evaluate("() => document.getElementById('b').getAttribute('style')"), json.loads(pg.evaluate('window.__state'))['align']], ['text-align: center; text-indent: 0px;', 'center'])
        pg.evaluate("KLED.cmd('align', 'justify')")
        check("back to what the book does leaves nothing on the paragraph", pg.evaluate("() => document.getElementById('b').getAttribute('style')"), None)
        pg.evaluate("() => { getSelection().setBaseAndExtent(document.getElementById('a').firstChild, 3, document.getElementById('c').firstChild, 0); KLED.cmd('align', 'right'); }")
        check('a selection lines up the paragraphs it takes words from, not one it only touches',
              pg.evaluate("() => ['a', 'b', 'c'].map(i => document.getElementById(i).getAttribute('style'))"), ['text-align: right;', 'text-align: right; text-indent: 0px;', None])

        pg = fresh(); pg.evaluate("KLED.select('all')"); o = json.loads(pg.evaluate('KLED.copy(false)'))
        check('select all, copied: paragraphs a blank line apart', [o['text'][:42], o['text'][-22:]], ['Chapter One\n\nAlpha one two three four five', '\nDelta eleven twelve.\n'])
        pg.evaluate(SET, ['a', 9]); pg.evaluate("KLED.select('toStart')")
        check('select to start', json.loads(pg.evaluate('KLED.copy(false)'))['text'], 'Chapter One\n\nAlpha one')
        pg.evaluate(SET, ['d', 6]); pg.evaluate("KLED.select('toEnd')")
        check('select to end', json.loads(pg.evaluate('KLED.copy(false)'))['text'], 'eleven twelve.')
        pg.evaluate(SET, ['b', 3]); pg.evaluate("KLED.select('paragraph')"); o = json.loads(pg.evaluate('KLED.copy(true)'))
        check('a paragraph cut keeps its italics and bold', o, {'text': 'Bravo italic words and bold words six seven.', 'html': 'Bravo <i>italic words</i> and <b>bold words</b> six seven.'})
        pg.evaluate(SET, ['d', 5]); pg.evaluate('([t, h]) => KLED.paste(t, h)', [o['text'], o['html']]); settle(pg)
        check('and pasted back', ['<p id="d">DeltaBravo <i>italic words</i> and <b>bold words</b> six seven. eleven twelve.</p>' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])
        b.close()
    server.shutdown(); shutil.rmtree(site, ignore_errors=True)
    print(f"\n{'ALL PASSED' if not failed else str(len(failed)) + ' FAILED: ' + '; '.join(failed)}")
    sys.exit(1 if failed else 0)

if __name__ == '__main__':
    main()
