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

        # The assistant: what it's given to work on, and its words going back in.
        pg = fresh()
        pg.evaluate("() => { const t = document.getElementById('a').firstChild; getSelection().setBaseAndExtent(t, 6, t, 19); }")
        o = json.loads(pg.evaluate('KLED.around()'))
        check('the assistant is given the words selected and the words before them', [o['text'], o['before'][-18:]], ['one two three', 'Chapter One\n\nAlpha'])
        pg.evaluate("() => getSelection().removeAllRanges()")       # as when its window takes the cursor away
        pg.evaluate('([t, h]) => KLED.putBack(t, h)', ['1 2 3', '<p>1 2 3</p>']); settle(pg)
        check('its words replace what was selected, though the selection was lost meanwhile',
              ['<p class="first" id="a">Alpha 1 2 3 four five.</p>' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])

        pg = fresh()
        pg.evaluate("() => { const t = document.getElementById('c').firstChild; getSelection().setBaseAndExtent(t, 0, document.getElementById('c').lastChild, 6); }")
        o = json.loads(pg.evaluate('KLED.around()'))
        check('words with styling among them are flagged: replacing them would lose it', [o['text'], o['more']], ['Charlie small caps here eight', True])
        pg.evaluate(SET, ['b', 3]); pg.evaluate("KLED.select('paragraph')")
        o = json.loads(pg.evaluate('KLED.around()'))
        check('a paragraph of words, italics and bold is not', [o['html'], o['more'], 'runs' in o], ['<p>Bravo <i>italic words</i> and <b>bold words</b> six seven.</p>', False, False])

        # Over several paragraphs it's given whole paragraphs, and each stays the paragraph it is.
        pg = fresh()
        pg.evaluate("() => { getSelection().setBaseAndExtent(document.getElementById('b').firstChild, 2, document.getElementById('c').firstChild, 7); }")
        o = json.loads(pg.evaluate('KLED.around()'))
        check('a selection over two paragraphs: the plain one whole, with italics and bold marked; the styled one left out',
              [o['runs'], o['skipped'], o['text']], [[['Bravo *italic words* and **bold words** six seven.']], 1, 'Bravo *italic words* and **bold words** six seven.'])
        pg.evaluate("() => getSelection().removeAllRanges()")
        n = pg.evaluate('(j) => KLED.putRuns(j)', json.dumps([{'run': 0, 'from': 0, 'count': 1, 'html': ['Bravo <i>words</i> &amp; more.']}])); settle(pg)
        body = pg.evaluate(BODY)
        check('its words go into that paragraph, and the styled one is untouched',
              [n, '<p id="b">Bravo <i>words</i> &amp; more.</p>' in body, '<p id="c">Charlie <span style="font-variant: small-caps;">small caps here</span> eight nine ten.</p>' in body, pg.evaluate(VALID)], [1, True, True, True])
        check('and the change is sent, to be saved and undone', len(pg.evaluate('window.__log')) >= 1 and 'Bravo <i>words</i> &amp; more.' in pg.evaluate('window.__log')[-1], True)

        pg = fresh()
        pg.evaluate("""() => { const d = document.getElementById('d'); const s = document.createElementNS('http://www.w3.org/1999/xhtml', 'span'); s.setAttribute('class', 'c3');
          s.textContent = d.textContent; d.textContent = ''; d.appendChild(s); d.setAttribute('class', 'c1'); KLED.select('all'); }""")
        o = json.loads(pg.evaluate('KLED.around()'))
        check('a whole chapter: paragraphs side by side go together; the heading and the styled paragraph stay out',
              [o['runs'], o['skipped']], [[['Alpha one two three four five.', 'Bravo *italic words* and **bold words** six seven.'], ['Item one', 'Item two'], ['Delta eleven twelve.']], 2])
        n = pg.evaluate('(j) => KLED.putRuns(j)', json.dumps([
            {'run': 0, 'from': 0, 'count': 2, 'html': ['One.', 'Two <b>bold</b>.', 'Three.']},
            {'run': 1, 'from': 0, 'count': 2, 'html': ['Only item']},
            {'run': 2, 'from': 0, 'count': 1, 'html': ['Delta <i>new</i>.']}])); settle(pg)
        body = pg.evaluate(BODY)
        check('more paragraphs back than went: the extra one is made like the last, without its id',
              '<h2>Chapter One</h2> <p class="first" id="a">One.</p> <p id="b">Two <b>bold</b>.</p><p>Three.</p> <p id="c">Charlie' in body, True)
        check('fewer back than went: the one left over goes', '<ul><li id="li1">Only item</li></ul>' in body, True)
        check('words wrapped in a styled box stay in it', '<p id="d" class="c1"><span class="c3">Delta <i>new</i>.</span></p>' in body, True)
        check('and the page is still sound', [n, pg.evaluate(VALID), pg.evaluate(IDS)], [6, True, 'ch1,a,b,c,li1,d'])
        check('markup that is not well formed is put right, not put in', pg.evaluate("""() => { KLED.select('all'); KLED.around();
          return KLED.putRuns(JSON.stringify([{ run: 0, from: 0, count: 1, html: ['<b>a <i>b</b> c</i> <script>x</script>&'] }])); }"""), 1)
        check('the page is sound after it', [pg.evaluate(VALID), '<script' in pg.evaluate("() => document.getElementById('a').innerHTML")], [True, False])

        # Carrying on writing.
        pg = fresh(); pg.evaluate(SET, ['d', 20])
        o = json.loads(pg.evaluate('KLED.around()'))
        check('with nothing selected it gets the words up to the cursor', [o['text'], o['before'][-20:], 'runs' in o], ['', 'Delta eleven twelve.', False])
        pg.evaluate("() => getSelection().removeAllRanges()")
        pg.evaluate('([t, h]) => KLED.addAfter(t, h)', ['Next one.\n\nAnd *another*.', '<p>Next one.</p><p>And <i>another</i>.</p>']); settle(pg)
        check('after a finished paragraph, what it writes starts new ones',
              ['<p id="d">Delta eleven twelve.</p><p>Next one.</p><p>And <i>another</i>.</p>' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])
        check('and the cursor is left at the end of them', pg.evaluate(CARET), lambda g: g[0].endswith('another.') and g[1].strip() == '')
        pg = fresh(); pg.evaluate(SET, ['d', 12]); pg.evaluate('KLED.around()')
        pg.evaluate('([t, h]) => KLED.addAfter(t, h)', ['and more', '<p>and more</p>']); settle(pg)
        check('in the middle of a line, it carries on the line', ['<p id="d">Delta eleven and more twelve.</p>' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])
        pg = fresh()
        pg.evaluate("() => { document.getElementById('d').textContent = 'He opened the door and'; }"); pg.evaluate(SET, ['d', 22]); pg.evaluate('KLED.around()')
        pg.evaluate('([t, h]) => KLED.addAfter(t, h)', ['saw the garden.\n\nIt was bright.', '<p>saw the garden.</p><p>It was bright.</p>']); settle(pg)
        body = pg.evaluate(BODY)
        check('after an unfinished sentence, its first paragraph finishes the line and the rest follow',
              ['<p id="d">He opened the door and saw the garden.</p>' in body, 'It was bright.</p> </section>' in body, pg.evaluate(VALID)], [True, True, True])
        pg = fresh()
        pg.evaluate("() => { const t = document.getElementById('a').firstChild; getSelection().setBaseAndExtent(t, 6, t, 30); }"); pg.evaluate('KLED.around()')
        pg.evaluate('([t, h]) => KLED.addAfter(t, h)', ['Six.', '<p>Six.</p>']); settle(pg)
        check('with words selected, it writes after them and they stay', '<p class="first" id="a">Alpha one two three four five.</p><p class="first">Six.</p>' in pg.evaluate(BODY), True)

        # Underlines and strikes, lists, quotes, links, tables and footnotes.
        SELW = """([id, a, b]) => { const el = document.getElementById(id); let n = el.firstChild; while (n && n.nodeType !== 3) n = n.firstChild || n.nextSibling;
          getSelection().setBaseAndExtent(n, a, n, b); document.body.focus(); }"""
        STATE = "() => { KLED.cmd('noop'); return new Promise(r => setTimeout(() => r(JSON.parse(window.__state)), 200)); }"
        pg = fresh(); pg.evaluate(SELW, ['a', 6, 9]); pg.evaluate("KLED.cmd('strike')"); settle(pg)
        check('words struck through are an <s>, not the <strike> books cannot have', ['Alpha <s>one</s> two' in pg.evaluate(BODY), '<strike' in pg.evaluate(BODY)], [True, False])
        check('and the toolbar knows', pg.evaluate(STATE)['strike'], True)
        pg.evaluate("KLED.cmd('strike')"); settle(pg)
        check('struck again, they are as they were', 'Alpha one two' in pg.evaluate(BODY), True)
        pg = fresh(); pg.evaluate(SELW, ['a', 6, 9]); pg.evaluate("KLED.cmd('underline')"); pg.evaluate("KLED.cmd('sup')"); settle(pg)
        check('underlined and raised', pg.evaluate(BODY), lambda g: '<u>' in g and '<sup>' in g and 'one</' in g)
        pg = fresh(); pg.evaluate(SET, ['b', 2]); pg.evaluate("KLED.cmd('clear')"); settle(pg)
        check('with nothing selected, clearing the formatting clears the paragraph the cursor is in',
              '<p id="b">Bravo italic words and bold words six seven.</p>' in pg.evaluate(BODY), True)

        pg = fresh(); pg.evaluate("() => { const a = document.getElementById('a').firstChild, d = document.getElementById('d').firstChild; getSelection().setBaseAndExtent(a, 2, document.getElementById('c').lastChild, 3); document.body.focus(); }")
        pg.evaluate("KLED.cmd('list', 'ol')"); settle(pg)
        body = pg.evaluate(BODY)
        check('paragraphs made a numbered list, keeping their ids, words and italics',
              ['<ol><li id="a">Alpha one two three four five.</li><li id="b">Bravo <i>italic words</i>' in body, '<li id="c">Charlie' in body, pg.evaluate(VALID)], [True, True, True])
        check('the selection stays on them', pg.evaluate("() => getSelection().toString().slice(0, 5)"), 'pha o')
        pg.evaluate("KLED.cmd('list', 'ol')"); settle(pg)
        body = pg.evaluate(BODY)
        check('and a list again, paragraphs', ['<ol' in body, '<p id="a">Alpha one' in body, '<p id="c">Charlie' in body, pg.evaluate(VALID)], [False, True, True, True])
        pg = fresh(); pg.evaluate(SET, ['d', 2]); pg.evaluate("KLED.cmd('list', 'ul')"); settle(pg)
        check('a paragraph made a list item just after a list joins it', '<ul><li id="li1">Item one</li><li>Item two</li><li id="d">Delta' in pg.evaluate(BODY), True)
        pg.evaluate("KLED.cmd('indent')"); settle(pg)
        check('in a level, it goes under the item before it', '<li>Item two<ul><li id="d">Delta eleven twelve.</li></ul></li></ul>' in pg.evaluate(BODY), True)
        check('the toolbar knows it is in a list', pg.evaluate(STATE)['list'], 'ul')
        pg.evaluate("KLED.cmd('outdent')"); settle(pg)
        check('out a level, it is back after it', '<li>Item two</li><li id="d">Delta eleven twelve.</li></ul>' in pg.evaluate(BODY), True)
        pg.evaluate("KLED.cmd('outdent')"); settle(pg)
        check('out of the list, a paragraph again', ['<li>Item two</li></ul><p id="d">Delta eleven twelve.</p>' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])
        pg = fresh(); pg.evaluate(SET, ['li1', 2]); pg.evaluate("KLED.cmd('list', 'ul')"); settle(pg)
        check('the first item of a list made a paragraph: the list goes on after it', '<p id="li1">Item one</p><ul><li>Item two</li></ul>' in pg.evaluate(BODY), True)

        pg = fresh(); pg.evaluate(SET, ['b', 2]); pg.evaluate("KLED.cmd('quote')"); settle(pg)
        check('a paragraph put in a quote', ['<blockquote><p id="b">Bravo' in pg.evaluate(BODY), pg.evaluate(STATE)['quote']], [True, True])
        pg.evaluate("KLED.cmd('quote')"); settle(pg)
        check('and out of it again', ['<blockquote' in pg.evaluate(BODY), '<p id="b">Bravo' in pg.evaluate(BODY)], [False, True])

        pg = fresh(); pg.evaluate(SELW, ['a', 6, 9]); pg.evaluate("""KLED.cmd('link', JSON.stringify({ href: 'https://example.org/?a=1&b=2', text: 'one' }))"""); settle(pg)
        check('selected words made a link', ['Alpha <a href="https://example.org/?a=1&amp;b=2">one</a> two' in pg.evaluate(BODY), pg.evaluate(VALID)], [True, True])
        pg.evaluate(SELW, ['a', 1, 1]); pg.evaluate("() => { const a = document.querySelector('#a a').firstChild; getSelection().collapse(a, 1); }")
        check('the toolbar knows the link and its words', [pg.evaluate(STATE)['link'], pg.evaluate(STATE)['text']], ['https://example.org/?a=1&b=2', 'one'])
        pg.evaluate("""KLED.cmd('link', JSON.stringify({ href: 'chapter2.xhtml', text: 'one' }))"""); settle(pg)
        check('changed, the link goes somewhere else', 'Alpha <a href="chapter2.xhtml">one</a> two' in pg.evaluate(BODY), True)
        pg.evaluate("() => { const a = document.querySelector('#a a').firstChild; getSelection().collapse(a, 1); }"); pg.evaluate("KLED.cmd('unlink')"); settle(pg)
        check('taken off, the words stay', '<p class="first" id="a">Alpha one two three four five.</p>' in pg.evaluate(BODY), True)
        pg = fresh(); pg.evaluate(SET, ['d', 6]); pg.evaluate("""KLED.cmd('link', JSON.stringify({ href: 'https://example.org/', text: 'a site' }))"""); settle(pg)
        check('with nothing selected, the link comes with its words', 'Delta <a href="https://example.org/">a site</a>eleven' in pg.evaluate(BODY), True)

        pg = fresh(); pg.evaluate(SET, ['a', 3]); pg.evaluate("""KLED.cmd('table', JSON.stringify({ rows: 3, cols: 2, header: true }))"""); settle(pg)
        t = pg.evaluate("() => { const t = document.querySelector('table'); return [t.previousElementSibling.id, t.querySelectorAll('thead th').length, t.querySelectorAll('tbody tr').length, t.querySelectorAll('tbody td').length, getSelection().anchorNode.closest('th') !== null]; }")
        check('a table goes after the paragraph, with its header row, and the cursor in its first cell', t, ['a', 2, 2, 4, True])
        check('the table is well-formed', pg.evaluate(VALID), True)
        check('the toolbar knows it is in a table', pg.evaluate(STATE)['table'], True)
        pg.evaluate("KLED.cmd('tableOp', 'rowBelow')"); pg.evaluate("KLED.cmd('tableOp', 'colRight')")
        check('a row added under the header goes at the top of the body, and a column added to every row',
              pg.evaluate("() => { const t = document.querySelector('table'); return [t.querySelectorAll('tbody tr').length, t.querySelector('thead tr').children.length, t.querySelector('tbody tr').children.length]; }"), [3, 3, 3])
        pg.evaluate("KLED.cmd('tableOp', 'delCol')"); pg.evaluate("KLED.cmd('tableOp', 'delRow')"); settle(pg)
        check('a column and a row taken out', pg.evaluate("() => { const t = document.querySelector('table'); return [t.querySelectorAll('tr').length, t.querySelector('tr').children.length]; }"), [3, 2])
        pg.evaluate("KLED.cmd('tableOp', 'delTable')"); settle(pg)
        check('the table taken out', [pg.evaluate("() => !!document.querySelector('table')"), pg.evaluate(VALID)], [False, True])

        pg = fresh(); pg.evaluate(SET, ['d', 5]); pg.evaluate("KLED.cmd('footnote', 'A second note.')")
        pg.evaluate(SET, ['a', 5]); pg.evaluate("KLED.cmd('footnote', 'The first note.')"); settle(pg)
        body = pg.evaluate(BODY)
        check('footnotes are numbered in the order they come, wherever they were added',
              [pg.evaluate("() => Array.from(document.querySelectorAll('a[id^=kolib-ref-] sup')).map(s => s.textContent).join(',')"),
               pg.evaluate("() => Array.from(document.querySelectorAll('#kolib-notes p')).map(p => p.textContent).join(' | ')")],
              ['1,2', '1. The first note. | 2. A second note.'])
        check('their notes link back, and the chapter is well-formed', ['<a href="#kolib-ref-2" role="doc-backlink">1.</a> The first note.' in body, pg.evaluate(VALID)], [True, True])
        check('the cursor is left just after the number', pg.evaluate(CARET)[1][:5], ' one ')
        pg.keyboard.type('X'); settle(pg)
        check('so typing carries on after it, not in it', pg.evaluate("() => document.querySelector('#a').textContent.slice(0, 8)"), 'Alpha1X ')
        pg.evaluate("() => { const r = document.getElementById('kolib-ref-2'); r.parentNode.removeChild(r); KLED.flush(); }")
        check('a footnote taken out takes its note, and the rest are renumbered',
              pg.evaluate("() => Array.from(document.querySelectorAll('#kolib-notes p')).map(p => p.textContent).join(' | ')"), '1. A second note.')

        pg = fresh(); pg.evaluate(SET, ['d', 5]); pg.evaluate("KLED.cmd('char', '\u2014')"); settle(pg)
        check('a special character goes in at the cursor', 'Delta\u2014 eleven' in pg.evaluate(BODY), True)
        pg = fresh(); pg.evaluate(SET, ['b', 3]); pg.evaluate(PASTE, ['x', '<p>An <u>under</u> and <strike>struck</strike> word</p>']); settle(pg)
        check('underlines and strikes come through a paste', 'An <u>under</u> and <s>struck</s> word' in pg.evaluate(BODY), True)
        b.close()
    server.shutdown(); shutil.rmtree(site, ignore_errors=True)
    print(f"\n{'ALL PASSED' if not failed else str(len(failed)) + ' FAILED: ' + '; '.join(failed)}")
    sys.exit(1 if failed else 0)

if __name__ == '__main__':
    main()
