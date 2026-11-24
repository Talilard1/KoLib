/*
 * KoLib's text editor, inside one chapter of a book.
 *
 * The chapter's body can be typed into. Changes go back to the app a moment after typing stops, as the
 * body's markup (or, for a chapter that wasn't valid XHTML, the whole document). Formatting comes from
 * the app's toolbar. window.KLE is the app's side.
 */
(function () {
  'use strict';

  var KLE = window.KLE || {
    start: function () { return '{}'; }, ready: function () {}, changed: function () {}, state: function () {},
    undo: function () {}, redo: function () {}, log: function () {},
  };
  var root = document.documentElement;
  var body = null;
  var whole = false;
  var lastSent = '';
  var timer = 0;
  var stateTimer = 0;
  var selectedImg = null;
  /** Where the cursor last was in the chapter, for a command that comes after a window has been open over the page. */
  var lastRange = null;
  /** An EPUB 3 book (footnotes are marked as such for e-readers that show them in a pop-up). */
  var epub3 = true;
  var original = typeof WeakSet !== 'undefined' ? new WeakSet() : null;
  /** The book's own styled spans, by what they say: a copy the browser makes of one (splitting a paragraph) is the book's too. */
  var ownSpans = {};
  var XHTML = 'http://www.w3.org/1999/xhtml';
  var OPS = 'http://www.idpf.org/2007/ops';
  /** Characters XML can't hold: one in the chapter would stop it being read back. */
  var BAD = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFE\uFFFF]/g;

  function parse(s) { try { return JSON.parse(s || '{}') || {}; } catch (e) { return {}; } }
  function esc(s) { return String(s).replace(BAD, '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

  // ---- what goes back to the app --------------------------------------------------------------

  /** A copy of the chapter without anything the editor added. */
  function cleanCopy() {
    var copy = (whole ? root : body).cloneNode(true);
    var b = whole ? copy.querySelector('body') : copy;
    if (b) { b.removeAttribute('contenteditable'); b.removeAttribute('spellcheck'); }
    if (whole) {
      var injected = copy.querySelectorAll('[data-kl]');
      for (var i = 0; i < injected.length; i++) injected[i].parentNode.removeChild(injected[i]);
    }
    var all = copy.querySelectorAll ? copy.querySelectorAll('*') : [];
    var list = [copy];
    for (var j = 0; j < all.length; j++) list.push(all[j]);
    for (var k = 0; k < list.length; k++) {
      var el = list[k];
      if (el.nodeType !== 1) continue;
      if (el.classList && el.classList.length) {
        var drop = [];
        for (var c = 0; c < el.classList.length; c++) if (/^kl-/.test(el.classList[c])) drop.push(el.classList[c]);
        for (var d = 0; d < drop.length; d++) el.classList.remove(drop[d]);
        if (!el.classList.length) el.removeAttribute('class');
      }
      if (el.hasAttribute && el.hasAttribute('srcset') && /[?&]kl-reload=/.test(el.getAttribute('srcset'))) el.removeAttribute('srcset');
    }
    return copy;
  }

  function spanKey(s) { return s.localName + '|' + (s.getAttribute('class') || '') + '|' + (s.getAttribute('style') || ''); }
  function indexOf(node) { var i = 0; for (var n = node.previousSibling; n; n = n.previousSibling) i++; return i; }
  function lengthOf(node) { return node.nodeType === 1 ? node.childNodes.length : (node.data || '').length; }

  /**
   * Spans the browser makes to keep a look when paragraphs are joined or text is pasted; the book never had
   * them. Taking them out moves the text they held, so the cursor (or what's selected) is put back after.
   */
  function unwrapBrowserSpans() {
    if (!original) return;
    var spans = body.querySelectorAll('span[style], font');
    var mine = [];
    dropBrowserStyles();
    for (var i = 0; i < spans.length; i++) {
      var s = spans[i];
      if (original.has(s)) continue;
      if (ownSpans[spanKey(s)]) { original.add(s); continue; }
      mine.push(s);
    }
    if (!mine.length) return;
    var sel = window.getSelection();
    var at = sel && sel.rangeCount ? { an: sel.anchorNode, ao: sel.anchorOffset, fn: sel.focusNode, fo: sel.focusOffset } : null;
    for (var k = 0; k < mine.length; k++) {
      var span = mine[k];
      var p = span.parentNode;
      if (!p) continue;
      if (at) {
        var idx = indexOf(span), count = span.childNodes.length;
        if (at.an === span) { at.an = p; at.ao = idx + at.ao; } else if (at.an === p && at.ao > idx) at.ao += count - 1;
        if (at.fn === span) { at.fn = p; at.fo = idx + at.fo; } else if (at.fn === p && at.fo > idx) at.fo += count - 1;
      }
      while (span.firstChild) p.insertBefore(span.firstChild, span);
      p.removeChild(span);
    }
    if (at && body.contains(at.an) && body.contains(at.fn)) {
      try { sel.setBaseAndExtent(at.an, Math.min(at.ao, lengthOf(at.an)), at.fn, Math.min(at.fo, lengthOf(at.fn))); } catch (e) {}
    }
  }

  /** Italics, bold and the like made here, given a look by the browser to match where they were pasted: just the tag is wanted. */
  function dropBrowserStyles() {
    if (!original) return;
    var els = body.querySelectorAll('i[style],b[style],em[style],strong[style],sup[style],sub[style],u[style],s[style]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (original.has(el)) continue;
      if (ownSpans[spanKey(el)]) original.add(el); else el.removeAttribute('style');
    }
  }

  /** A paragraph split in two (by Enter, or by pasting) leaves both halves with its id: only the first keeps it. */
  function dropRepeatedIds() {
    var els = body.querySelectorAll('[id]');
    var first = {};
    for (var i = 0; i < els.length; i++) {
      var el = els[i], id = el.getAttribute('id');
      var f = first[id];
      if (!f) { first[id] = el; continue; }
      // Two the book came with are the book's business.
      if (original && original.has(el) && original.has(f)) continue;
      // An empty line left above (Enter at the start of a paragraph) gives the id up to the words.
      if (!/\S/.test(f.textContent) && /\S/.test(el.textContent)) { f.removeAttribute('id'); first[id] = el; } else el.removeAttribute('id');
    }
  }

  function tidy() {
    unwrapBrowserSpans();
    dropRepeatedIds();
    fixStrikes();
    tidyNotes();
  }

  function serialize() {
    var ser = new XMLSerializer();
    var copy = cleanCopy();
    if (whole) return ser.serializeToString(copy).replace(BAD, '');
    var out = '';
    for (var n = copy.firstChild; n; n = n.nextSibling) out += ser.serializeToString(n);
    return out.replace(BAD, '');
  }

  function send() {
    clearTimeout(timer);
    timer = 0;
    tidy();
    var s = serialize();
    if (s === lastSent) return false;
    lastSent = s;
    KLE.changed(s);
    return true;
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(send, 700);
  }

  // ---- the toolbar's state ----------------------------------------------------------------------

  var blockNames = /^(p|h[1-6]|li|blockquote|pre|dd|dt|div|td|th|figcaption)$/i;
  /** What a paragraph can sit straight inside. */
  var holdsBlocks = /^(body|section|article|main|aside|header|footer|div|blockquote|li|dd|td|th|figure)$/i;

  function blockOf(node) {
    while (node && node !== body) {
      if (node.nodeType === 1 && blockNames.test(node.localName)) return node;
      node = node.parentNode;
    }
    return null;
  }

  function alignOf(block) {
    if (!block) return null;
    var a = window.getComputedStyle(block).textAlign;
    if (a === 'center' || a === 'right' || a === 'justify') return a;
    if (a === 'end') return 'right';
    return 'left';
  }

  function reportState() {
    clearTimeout(stateTimer);
    stateTimer = setTimeout(function () {
      var sel = window.getSelection();
      var has = !!(sel && sel.rangeCount);
      var block = has ? blockOf(sel.anchorNode) : null;
      var range = has && !sel.isCollapsed;
      // Over many paragraphs, asking the browser whether it's all bold is slow, and says little.
      var wide = range && blockOf(sel.focusNode) !== block;
      var at = has && body.contains(sel.anchorNode) ? sel.anchorNode : null;
      if (at) lastRange = sel.getRangeAt(0).cloneRange();
      var li = at ? closestIn(at, 'li') : null;
      var link = at ? closestIn(at, 'a[href]') : null;
      var st = {
        bold: !wide && safeState('bold'),
        italic: !wide && safeState('italic'),
        underline: !wide && !!(at && closestIn(at, 'u')),
        strike: !wide && !!(at && closestIn(at, 's,strike,del')),
        sup: !wide && !!(at && closestIn(at, 'sup')),
        sub: !wide && !!(at && closestIn(at, 'sub')),
        block: block ? block.localName.toLowerCase() : null,
        align: alignOf(block),
        list: li && li.parentNode ? li.parentNode.localName.toLowerCase() : null,
        indent: !!(li && li.previousElementSibling && li.previousElementSibling.localName === 'li'),
        quote: !!(at && closestIn(at, 'blockquote')),
        link: link ? link.getAttribute('href') : null,
        text: link ? link.textContent.slice(0, 300) : (range && !wide ? sel.toString().slice(0, 300) : ''),
        table: !!(at && closestIn(at, 'td,th')),
        selection: range,
        image: selectedImg ? selectedImg.getAttribute('src') : null,
        scrollY: window.scrollY,
      };
      try { KLE.state(JSON.stringify(st)); } catch (e) {}
    }, 80);
  }

  function safeState(cmd) { try { return document.queryCommandState(cmd); } catch (e) { return false; } }

  // ---- commands from the toolbar ------------------------------------------------------------------

  function insertHtml(html) {
    if (!document.execCommand('insertHTML', false, html)) {
      // A fallback for when the browser won't: put it after the current block, with the cursor at its end.
      var sel = window.getSelection();
      var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
      var holder = document.createElementNS(XHTML, 'div');
      holder.innerHTML = html;
      var after = block ? block : body.lastChild;
      var last = null;
      while (holder.lastChild) {
        var n = holder.lastChild;
        if (!last) last = n;
        if (after && after.parentNode) after.parentNode.insertBefore(n, after.nextSibling);
        else body.insertBefore(n, body.firstChild);
      }
      if (last && sel) {
        var r = document.createRange();
        r.selectNodeContents(last);
        r.collapse(false);
        sel.removeAllRanges();
        sel.addRange(r);
      }
    }
  }

  /** Puts a new block (a scene break, a picture) after the paragraph the cursor is in. */
  function insertBlock(el) {
    var sel = window.getSelection();
    var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
    // Out of anything a paragraph can't go in (a list, a table, a heading), but no further.
    while (block && block.parentNode && block.parentNode !== body && !holdsBlocks.test(block.parentNode.localName)) block = block.parentNode;
    if (block && block.parentNode) {
      block.parentNode.insertBefore(el, block.nextSibling);
      block.parentNode.insertBefore(document.createTextNode('\n'), el);
    } else if (sel && sel.rangeCount && body.contains(sel.anchorNode)) {
      sel.getRangeAt(0).insertNode(el);
    } else {
      body.appendChild(el);
    }
    // The cursor goes after it, ready to carry on typing.
    var next = el.nextElementSibling;
    var r = document.createRange();
    if (next) { r.setStart(next, 0); } else { r.setStartAfter(el); }
    r.collapse(true);
    sel.removeAllRanges();
    sel.addRange(r);
  }

  function selectImage(img) {
    if (selectedImg) selectedImg.classList.remove('kl-selected');
    selectedImg = img;
    if (img) img.classList.add('kl-selected');
    reportState();
  }

  /** The paragraphs (headings, list items) the selection touches; the one the cursor is in when nothing's selected. */
  function selectedBlocks() {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return [];
    var r = sel.getRangeAt(0);
    if (r.collapsed) { var one = blockOf(r.startContainer); return one ? [one] : []; }
    var out = [];
    var all = body.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,dd,dt,div,td,th,figcaption');
    var br = document.createRange();
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      // Only the innermost: a box of paragraphs is left to its paragraphs.
      if (el.querySelector('p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,dd,dt,div,td,th,figcaption')) continue;
      br.selectNodeContents(el);
      if (r.compareBoundaryPoints(Range.START_TO_END, br) <= 0 || r.compareBoundaryPoints(Range.END_TO_START, br) >= 0) continue;
      // The selection must take in some of its words (or all of it, if it has none): ending just at its start doesn't count.
      var whole = r.compareBoundaryPoints(Range.START_TO_START, br) <= 0 && r.compareBoundaryPoints(Range.END_TO_END, br) >= 0;
      var part = r.cloneRange();
      if (part.compareBoundaryPoints(Range.START_TO_START, br) < 0) part.setStart(br.startContainer, br.startOffset);
      if (part.compareBoundaryPoints(Range.END_TO_END, br) > 0) part.setEnd(br.endContainer, br.endOffset);
      if (whole || part.toString().length) out.push(el);
    }
    return out;
  }

  /** Lines the chosen paragraphs up: left, center, right or justify. What the book already does is left to the book. */
  function align(how) {
    var blocks = selectedBlocks();
    for (var i = 0; i < blocks.length; i++) {
      var el = blocks[i];
      el.style.removeProperty('text-align');
      // An indent put there with centred or right-hand text goes with it.
      if (/^0(px|em|%)?$/.test(el.style.textIndent)) el.style.removeProperty('text-indent');
      if (alignOf(el) !== how) {
        el.style.setProperty('text-align', how);
        if ((how === 'center' || how === 'right') && parseFloat(window.getComputedStyle(el).textIndent)) el.style.setProperty('text-indent', '0');
      }
      if (!el.getAttribute('style')) el.removeAttribute('style');
    }
    return blocks.length;
  }

  function cmd(name, arg) {
    if (!body) return;
    ensureCursor();
    switch (name) {
      case 'bold': document.execCommand('bold'); break;
      case 'italic': document.execCommand('italic'); break;
      case 'underline': document.execCommand('underline'); break;
      case 'strike': document.execCommand('strikeThrough'); fixStrikes(); break;
      case 'sup': document.execCommand('superscript'); break;
      case 'sub': document.execCommand('subscript'); break;
      case 'clear': if (!clearFormatting()) return; break;
      case 'list': if (!toggleList(arg === 'ol' ? 'ol' : 'ul')) return; break;
      case 'indent': if (!indentItem(false)) return; break;
      case 'outdent': if (!indentItem(true)) return; break;
      case 'quote': if (!toggleQuote()) return; break;
      case 'link': if (!setLink(arg)) return; break;
      case 'unlink': if (!removeLink()) return; break;
      case 'table': if (!insertTable(arg)) return; break;
      case 'tableOp': if (!tableOp(arg)) return; break;
      case 'footnote': if (!addFootnote(arg)) return; break;
      case 'char': if (!arg) return; document.execCommand('insertText', false, arg); break;
      case 'block': document.execCommand('formatBlock', false, arg || 'p'); break;
      case 'align': if (!align(arg)) return; break;
      case 'scenebreak': {
        var br = document.createElementNS(XHTML, 'p');
        br.setAttribute('class', 'scene-break');
        br.setAttribute('style', 'text-align: center; text-indent: 0;');
        br.appendChild(document.createTextNode('* * *'));
        insertBlock(br);
        break;
      }
      case 'image': {
        var holder = document.createElementNS(XHTML, 'p');
        holder.setAttribute('style', 'text-align: center; text-indent: 0;');
        var img = document.createElementNS(XHTML, 'img');
        img.setAttribute('src', arg);
        img.setAttribute('alt', '');
        holder.appendChild(img);
        insertBlock(holder);
        break;
      }
      case 'reloadImage': {
        var imgs = body.querySelectorAll('img');
        for (var i = 0; i < imgs.length; i++) {
          var src = imgs[i].getAttribute('src');
          if (src === arg) imgs[i].setAttribute('srcset', new URL(src, document.baseURI).href + '?kl-reload=' + Date.now());
        }
        return;
      }
      default: return;
    }
    schedule();
    reportState();
  }

  // ---- underlines and strikes, lists, quotes, links, tables and footnotes --------------------------------------

  function make(name) { return document.createElementNS(XHTML, name); }
  function elementOf(node) { return node && node.nodeType !== 1 ? node.parentNode : node; }

  /** The nearest element round [node] (or [node] itself) matching [selector], in the chapter. */
  function closestIn(node, selector) {
    var e = elementOf(node);
    var c = e && e.closest ? e.closest(selector) : null;
    return c && c !== body && body.contains(c) ? c : null;
  }

  /** The cursor back in the page, where it was before a window opened over it. */
  function ensureCursor() {
    var sel = window.getSelection();
    var inside = sel && sel.rangeCount && body.contains(sel.getRangeAt(0).commonAncestorContainer);
    body.focus();
    if (!inside && lastRange && body.contains(lastRange.commonAncestorContainer)) {
      try { sel.removeAllRanges(); sel.addRange(lastRange); } catch (e) {}
    }
  }

  /** Where the selection is, to put back after paragraphs are moved about ([swap] gives an element's stand-in). */
  function saveSel() {
    var s = window.getSelection();
    return s && s.rangeCount ? { an: s.anchorNode, ao: s.anchorOffset, fn: s.focusNode, fo: s.focusOffset } : null;
  }

  function restoreSel(at, swap) {
    if (!at) return false;
    var an = swap ? swap(at.an) : at.an, fn = swap ? swap(at.fn) : at.fn;
    if (!an || !fn || !body.contains(an) || !body.contains(fn)) return false;
    try { window.getSelection().setBaseAndExtent(an, Math.min(at.ao, lengthOf(an)), fn, Math.min(at.fo, lengthOf(fn))); return true; } catch (e) { return false; }
  }

  function swapper(pairs) {
    return function (n) { for (var i = 0; i < pairs.length; i++) if (pairs[i][0] === n) return pairs[i][1]; return n; };
  }

  function caretIn(el, atEnd) {
    var r = document.createRange();
    r.selectNodeContents(el);
    r.collapse(!atEnd);
    var s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
  }

  function caretAfter(node) {
    var r = document.createRange();
    r.setStartAfter(node);
    r.collapse(true);
    var s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
  }

  /** Puts [node] in place of what's selected (or at the cursor), with the cursor after it. */
  function insertInline(node) {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return false;
    var r = sel.getRangeAt(0);
    if (!body.contains(r.commonAncestorContainer)) return false;
    if (!r.collapsed) r.deleteContents();
    r.insertNode(node);
    caretAfter(node);
    return true;
  }

  /** [el] as a [name] instead, with what's in it and its attributes. */
  function renamed(el, name) {
    var n = make(name);
    for (var i = 0; i < el.attributes.length; i++) {
      var a = el.attributes[i];
      try { n.setAttributeNS(a.namespaceURI, a.name, a.value); } catch (e) {}
    }
    while (el.firstChild) n.appendChild(el.firstChild);
    el.parentNode.replaceChild(n, el);
    return n;
  }

  /** The browser strikes words through with <strike>, which a book can't have: <s> it is. */
  function fixStrikes() {
    var old = body.querySelectorAll('strike');
    var pairs = [];
    for (var i = 0; i < old.length; i++) if (!(original && original.has(old[i]))) pairs.push([old[i], null]);
    if (!pairs.length) return;
    var at = saveSel();
    for (var j = 0; j < pairs.length; j++) pairs[j][1] = renamed(pairs[j][0], 's');
    restoreSel(at, swapper(pairs));
  }

  /** Clears the bold, italics and the like from what's selected (or the paragraph the cursor's in), and how it's lined up. */
  function clearFormatting() {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return false;
    var at = null;
    if (sel.isCollapsed) {
      var block = blockOf(sel.anchorNode);
      if (!block) return false;
      at = saveSel();
      var r = document.createRange();
      r.selectNodeContents(block);
      sel.removeAllRanges();
      sel.addRange(r);
    }
    var blocks = selectedBlocks();
    document.execCommand('removeFormat');
    for (var i = 0; i < blocks.length; i++) {
      var el = blocks[i];
      if (!el.parentNode) continue;
      el.style.removeProperty('text-align');
      if (/^0(px|em|%)?$/.test(el.style.textIndent)) el.style.removeProperty('text-indent');
      if (!el.getAttribute('style')) el.removeAttribute('style');
    }
    if (at && !restoreSel(at)) sel.collapseToStart();
    return true;
  }

  /** What a paragraph in a list item can be made from. */
  var itemBlocks = /^(p|h[1-6]|div)$/i;

  /** A list item made a paragraph again (or its paragraphs, if it has some), where it was: the list is split round it. */
  function unlist(li) {
    var list = li.parentNode, parent = list.parentNode;
    var rest = null;
    while (li.nextSibling) {
      if (!rest) { rest = list.cloneNode(false); rest.removeAttribute('id'); }
      rest.appendChild(li.nextSibling);
    }
    var frag = document.createDocumentFragment();
    var p = null;
    while (li.firstChild) {
      var n = li.firstChild;
      if (n.nodeType === 1 && blockTags.test(n.localName)) { p = null; frag.appendChild(n); continue; }
      if (!p) {
        if (n.nodeType === 3 && !/\S/.test(n.data)) { li.removeChild(n); continue; }
        p = make('p');
        frag.appendChild(p);
      }
      p.appendChild(n);
    }
    if (!frag.firstChild) { p = make('p'); p.appendChild(make('br')); frag.appendChild(p); }
    var first = frag.firstChild, last = frag.lastChild;
    if (li.getAttribute('id') && !first.getAttribute('id')) first.setAttribute('id', li.getAttribute('id'));
    parent.insertBefore(frag, list.nextSibling);
    if (rest && rest.querySelector('li')) parent.insertBefore(rest, last.nextSibling);
    list.removeChild(li);
    if (!list.querySelector('li')) parent.removeChild(list);
    return first;
  }

  /** Paragraphs made the items of a list; side by side, they're one list (joined to one of the kind just before or after). */
  function listify(blocks, kind, pairs) {
    var groups = [];
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      if (!itemBlocks.test(b.localName)) continue;
      var g = groups[groups.length - 1];
      if (g && g.last.parentNode === b.parentNode && g.last.nextElementSibling === b) { g.items.push(b); g.last = b; }
      else groups.push({ items: [b], last: b });
    }
    for (var k = 0; k < groups.length; k++) {
      var items = groups[k].items;
      var list = make(kind);
      items[0].parentNode.insertBefore(list, items[0]);
      for (var j = 0; j < items.length; j++) {
        var li = make('li');
        if (items[j].getAttribute('id')) li.setAttribute('id', items[j].getAttribute('id'));
        while (items[j].firstChild) li.appendChild(items[j].firstChild);
        list.appendChild(li);
        items[j].parentNode.removeChild(items[j]);
        pairs.push([items[j], li]);
      }
      var before = list.previousElementSibling, after = list.nextElementSibling;
      if (after && after.localName === kind) { while (after.firstChild) list.appendChild(after.firstChild); after.parentNode.removeChild(after); }
      if (before && before.localName === kind) { while (list.firstChild) before.appendChild(list.firstChild); list.parentNode.removeChild(list); }
    }
    return groups.length > 0;
  }

  /** Makes the paragraphs selected a list of [kind] (ul or ol); a list of that kind already, they're paragraphs again. */
  function toggleList(kind) {
    var blocks = selectedBlocks();
    if (!blocks.length) return false;
    var at = saveSel(), pairs = [], items = [], others = [];
    for (var i = 0; i < blocks.length; i++) {
      var li = blocks[i].localName === 'li' ? blocks[i] : closestIn(blocks[i], 'li');
      if (li) { if (items.indexOf(li) < 0) items.push(li); } else others.push(blocks[i]);
    }
    if (!others.length) {
      var same = items.every(function (it) { return it.parentNode.localName === kind; });
      if (same) {
        for (var j = 0; j < items.length; j++) pairs.push([items[j], unlist(items[j])]);
      } else {
        var lists = [];
        items.forEach(function (it) { if (lists.indexOf(it.parentNode) < 0) lists.push(it.parentNode); });
        lists.forEach(function (l) {
          if (l.localName === kind) return;
          var n = renamed(l, kind);
          if (kind === 'ul') { n.removeAttribute('start'); n.removeAttribute('reversed'); n.removeAttribute('type'); }
          pairs.push([l, n]);
        });
      }
    } else if (!listify(others, kind, pairs)) return false;
    if (!restoreSel(at, swapper(pairs)) && pairs.length && pairs[0][1]) caretIn(pairs[0][1], true);
    return true;
  }

  /** The list item the cursor is in goes in a level (under the one before it), or out a level (out of a list, a paragraph). */
  function indentItem(out) {
    var sel = window.getSelection();
    var li = sel && sel.rangeCount ? closestIn(sel.anchorNode, 'li') : null;
    if (!li) return false;
    var at = saveSel(), list = li.parentNode;
    if (!out) {
      var prev = li.previousElementSibling;
      if (!prev || prev.localName !== 'li') return false;
      var inner = prev.lastElementChild;
      if (!inner || inner.localName !== list.localName) { inner = make(list.localName); prev.appendChild(inner); }
      inner.appendChild(li);
    } else {
      var holder = list.parentNode;
      if (!holder || holder.localName !== 'li') {
        var p = unlist(li);
        if (!restoreSel(at, swapper([[li, p]]))) caretIn(p, true);
        return true;
      }
      // The items after it go with it, as a list under it.
      var rest = null;
      while (li.nextSibling) {
        if (!rest) { rest = list.cloneNode(false); rest.removeAttribute('id'); }
        rest.appendChild(li.nextSibling);
      }
      if (rest && rest.querySelector('li')) li.appendChild(rest);
      holder.parentNode.insertBefore(li, holder.nextSibling);
      if (!list.querySelector('li')) list.parentNode.removeChild(list);
    }
    if (!restoreSel(at)) caretIn(li, true);
    return true;
  }

  /** The outermost element of [block] that sits straight in a box of paragraphs (a list item's list, say). */
  function outerOf(block) {
    var e = block;
    while (e.parentNode && e.parentNode !== body && !holdsBlocks.test(e.parentNode.localName)) e = e.parentNode;
    return e;
  }

  /** Puts the paragraphs selected in a quote; in one, takes them out of it. */
  function toggleQuote() {
    var blocks = selectedBlocks();
    if (!blocks.length || /^(td|th)$/i.test(blocks[0].localName)) return false;
    var at = saveSel(), pairs = [];
    var q = closestIn(blocks[0], 'blockquote');
    if (q) {
      // What was in it goes where it was; words straight in it become a paragraph.
      var parent = q.parentNode, p = null;
      while (q.firstChild) {
        var n = q.firstChild;
        if (n.nodeType === 1 && blockTags.test(n.localName)) { p = null; parent.insertBefore(n, q); continue; }
        if (!p) {
          if (n.nodeType === 3 && !/\S/.test(n.data)) { parent.insertBefore(n, q); continue; }
          p = make('p');
          parent.insertBefore(p, q);
          if (!pairs.length) pairs.push([q, p]);
        }
        p.appendChild(n);
      }
      parent.removeChild(q);
    } else {
      var first = outerOf(blocks[0]), last = outerOf(blocks[blocks.length - 1]);
      if (last.parentNode !== first.parentNode) last = first;
      var quote = make('blockquote');
      first.parentNode.insertBefore(quote, first);
      for (var m = first; m;) {
        var next = m.nextSibling;
        quote.appendChild(m);
        if (m === last) break;
        m = next;
      }
    }
    restoreSel(at, swapper(pairs));
    return true;
  }

  /** A link to [href] (a web address, or a place in the book) on what's selected, the link the cursor's in, or new words. */
  function setLink(arg) {
    var o = parse(arg);
    var href = String(o.href || '').replace(BAD, '').trim();
    var text = String(o.text || '').replace(BAD, '');
    if (!href) return false;
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return false;
    var a = closestIn(sel.anchorNode, 'a[href]');
    if (a) {
      a.setAttribute('href', href);
      if (text && text !== a.textContent) a.textContent = text;
      caretAfter(a);
    } else if (!sel.isCollapsed && (!text || text === sel.toString())) {
      document.execCommand('createLink', false, href);
    } else {
      var made = make('a');
      made.setAttribute('href', href);
      made.appendChild(document.createTextNode(text || href));
      if (!insertInline(made)) return false;
    }
    return true;
  }

  /** Takes the link off the words the cursor is in (or those selected); the words stay. */
  function removeLink() {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return false;
    var a = closestIn(sel.anchorNode, 'a[href]');
    if (!a) {
      if (sel.isCollapsed) return false;
      document.execCommand('unlink');
      return true;
    }
    var at = saveSel(), parent = a.parentNode, first = a.firstChild;
    while (a.firstChild) parent.insertBefore(a.firstChild, a);
    parent.removeChild(a);
    if (!restoreSel(at, swapper([[a, parent]])) && first) caretAfter(first);
    return true;
  }

  var CELL = 'border: 1px solid; padding: 0.25em 0.5em; vertical-align: top;';

  /** An empty cell for a table: like [like] (its look), or new. */
  function blankCell(tag, like) {
    var c = make(tag);
    if (like && like.localName === tag) {
      if (like.getAttribute('style')) c.setAttribute('style', like.getAttribute('style'));
      if (like.getAttribute('class')) c.setAttribute('class', like.getAttribute('class'));
    } else c.setAttribute('style', tag === 'td' ? CELL + ' text-align: left;' : CELL);
    c.appendChild(make('br'));
    return c;
  }

  /** A new table after the paragraph the cursor is in: {rows, cols, header}, the header (if any) its first row. */
  function insertTable(arg) {
    var o = parse(arg);
    var rows = Math.max(1, Math.min(100, (o.rows | 0) || 3)), cols = Math.max(1, Math.min(12, (o.cols | 0) || 3));
    var t = make('table'), r, i, j;
    t.setAttribute('style', 'border-collapse: collapse; margin: 1em 0; width: 100%;');
    if (o.header) {
      var head = make('thead');
      r = make('tr');
      for (j = 0; j < cols; j++) r.appendChild(blankCell('th', null));
      head.appendChild(r);
      t.appendChild(head);
      rows--;
    }
    if (rows > 0) {
      var tb = make('tbody');
      for (i = 0; i < rows; i++) {
        r = make('tr');
        for (j = 0; j < cols; j++) r.appendChild(blankCell('td', null));
        tb.appendChild(r);
      }
      t.appendChild(tb);
    }
    insertBlock(t);
    caretIn(t.querySelector('th,td'), false);
    return true;
  }

  function cellsOf(row) {
    var out = [];
    for (var n = row.firstElementChild; n; n = n.nextElementSibling) if (/^(td|th)$/i.test(n.localName)) out.push(n);
    return out;
  }

  function rowsOf(table) {
    return Array.prototype.filter.call(table.querySelectorAll('tr'), function (tr) { return tr.closest('table') === table; });
  }

  function dropTable(table) {
    var next = table.nextElementSibling || table.previousElementSibling;
    var parent = table.parentNode;
    parent.removeChild(table);
    if (!next) { next = make('p'); next.appendChild(make('br')); parent.appendChild(next); }
    caretIn(next, false);
    return true;
  }

  /** Rows and columns added round the cell the cursor's in, or taken out; or the whole table taken out. */
  function tableOp(op) {
    var sel = window.getSelection();
    var cell = sel && sel.rangeCount ? closestIn(sel.anchorNode, 'td,th') : null;
    if (!cell) return false;
    var row = cell.parentNode, table = row.closest('table');
    if (!table || !body.contains(table)) return false;
    var col = cellsOf(row).indexOf(cell), next = null, rows, k;
    if (op === 'rowAbove' || op === 'rowBelow') {
      var inHead = row.parentNode.localName === 'thead';
      var tr = make('tr');
      cellsOf(row).forEach(function (c) { tr.appendChild(blankCell(inHead && op === 'rowBelow' ? 'td' : c.localName, c)); });
      if (inHead && op === 'rowBelow') {
        var tb = table.querySelector('tbody');
        if (!tb || tb.closest('table') !== table) { tb = make('tbody'); table.appendChild(tb); }
        tb.insertBefore(tr, tb.firstChild);
      } else row.parentNode.insertBefore(tr, op === 'rowAbove' ? row : row.nextSibling);
      var made = cellsOf(tr);
      next = made[Math.min(col, made.length - 1)];
    } else if (op === 'colLeft' || op === 'colRight') {
      rows = rowsOf(table);
      for (k = 0; k < rows.length; k++) {
        var cs = cellsOf(rows[k]), ref = cs[Math.min(col, cs.length - 1)];
        if (!ref) continue;
        var c2 = blankCell(ref.localName, ref);
        rows[k].insertBefore(c2, op === 'colLeft' ? ref : ref.nextSibling);
        if (rows[k] === row) next = c2;
      }
    } else if (op === 'delRow') {
      rows = rowsOf(table);
      var at = rows.indexOf(row);
      var stays = rows[at + 1] || rows[at - 1];
      if (!stays) return dropTable(table);
      row.parentNode.removeChild(row);
      var sc = cellsOf(stays);
      next = sc[Math.min(col, sc.length - 1)];
    } else if (op === 'delCol') {
      rows = rowsOf(table);
      for (k = 0; k < rows.length; k++) {
        var cells = cellsOf(rows[k]);
        if (cells[col]) rows[k].removeChild(cells[col]);
        if (!cellsOf(rows[k]).length) rows[k].parentNode.removeChild(rows[k]);
      }
      if (!rowsOf(table).length) return dropTable(table);
      var left = cellsOf(row.parentNode ? row : rowsOf(table)[0]);
      next = left[Math.min(col, left.length - 1)];
    } else if (op === 'delTable') {
      return dropTable(table);
    } else return false;
    // Sections of the table left with no rows go.
    Array.prototype.forEach.call(table.querySelectorAll('thead,tbody,tfoot'), function (s) { if (!s.querySelector('tr')) s.parentNode.removeChild(s); });
    if (next) caretIn(next, false);
    return true;
  }

  var REF = 'kolib-ref-', NOTE = 'kolib-note-';

  /** The box at the end of the chapter that its footnotes go in. */
  function notesBox() {
    var box = document.getElementById('kolib-notes');
    if (box && body.contains(box)) return box;
    box = make(epub3 ? 'section' : 'div');
    box.setAttribute('id', 'kolib-notes');
    if (epub3) box.setAttributeNS(OPS, 'epub:type', 'footnotes');
    box.setAttribute('style', 'margin-top: 2em; padding-top: 0.5em; border-top: 1px solid; font-size: 0.85em;');
    body.appendChild(document.createTextNode('\n'));
    body.appendChild(box);
    return box;
  }

  /** A footnote: its number where the cursor is, linked to [text] at the end of the chapter (and back). */
  function addFootnote(text) {
    text = String(text || '').replace(BAD, '').replace(/\s+/g, ' ').trim();
    if (!text) return false;
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount || !body.contains(sel.anchorNode)) return false;
    if (!sel.isCollapsed) sel.collapseToEnd();
    var inLink = closestIn(sel.anchorNode, 'a');
    if (inLink) caretAfter(inLink);
    var used = 0;
    var marked = body.querySelectorAll('[id^="' + REF + '"],[id^="' + NOTE + '"]');
    for (var i = 0; i < marked.length; i++) used = Math.max(used, parseInt(marked[i].getAttribute('id').replace(/^kolib-(ref|note)-/, ''), 10) || 0);
    var key = used + 1;
    var ref = make('a');
    ref.setAttribute('id', REF + key);
    ref.setAttribute('href', '#' + NOTE + key);
    if (epub3) { ref.setAttributeNS(OPS, 'epub:type', 'noteref'); ref.setAttribute('role', 'doc-noteref'); }
    var sup = make('sup');
    sup.appendChild(document.createTextNode('0'));
    ref.appendChild(sup);
    if (!insertInline(ref)) return false;
    var note = make(epub3 ? 'aside' : 'div');
    note.setAttribute('id', NOTE + key);
    if (epub3) { note.setAttributeNS(OPS, 'epub:type', 'footnote'); note.setAttribute('role', 'doc-footnote'); }
    var p = make('p');
    p.setAttribute('style', 'text-indent: 0; margin: 0.3em 0;');
    var back = make('a');
    back.setAttribute('href', '#' + REF + key);
    if (epub3) back.setAttribute('role', 'doc-backlink');
    back.appendChild(document.createTextNode('0.'));
    p.appendChild(back);
    p.appendChild(document.createTextNode(' ' + text));
    note.appendChild(p);
    notesBox().appendChild(note);
    tidyNotes();
    return true;
  }

  function setText(el, s) { if (el && el.textContent !== s) el.textContent = s; }

  /** The chapter's footnotes numbered in the order they come, their notes in that order too; a note whose number has gone goes too. */
  function tidyNotes() {
    var refs = body.querySelectorAll('a[id^="' + REF + '"]');
    var box = document.getElementById('kolib-notes');
    if (!refs.length && !box) return;
    var notes = box ? box.querySelectorAll('[id^="' + NOTE + '"]') : [];
    var byKey = {}, inOrder = [];
    for (var i = 0; i < notes.length; i++) byKey[notes[i].getAttribute('id').slice(NOTE.length)] = notes[i];
    var n = 0;
    for (var j = 0; j < refs.length; j++) {
      var key = refs[j].getAttribute('id').slice(REF.length);
      var note = byKey[key];
      if (!note) continue;
      n++;
      setText(refs[j].querySelector('sup') || refs[j], String(n));
      setText(note.querySelector('a[href="#' + REF + key + '"]'), n + '.');
      inOrder.push(note);
      delete byKey[key];
    }
    var gone = Object.keys(byKey);
    var now = Array.prototype.filter.call(notes, function (x) { return inOrder.indexOf(x) >= 0; });
    var moved = now.some(function (x, k) { return x !== inOrder[k]; });
    if (!gone.length && !moved && (!box || notes.length)) return;
    var at = saveSel();
    gone.forEach(function (k) { var x = byKey[k]; if (x.parentNode) x.parentNode.removeChild(x); });
    if (moved) inOrder.forEach(function (x) { box.appendChild(x); });
    if (box && !box.querySelector('[id^="' + NOTE + '"]')) box.parentNode.removeChild(box);
    restoreSel(at);
  }

  // ---- selecting, copying and pasting ---------------------------------------------------------------

  function textNodes() {
    var out = [];
    var w = document.createTreeWalker(body, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_CDATA_SECTION, null);
    for (var n = w.nextNode(); n; n = w.nextNode()) out.push(n);
    return out;
  }

  /** The first and last places text can be, in words rather than between paragraphs. */
  function ends() {
    var nodes = textNodes().filter(function (n) { return /\S/.test(n.data); });
    if (!nodes.length) return null;
    var last = nodes[nodes.length - 1];
    return { first: nodes[0], firstAt: nodes[0].data.search(/\S/), last: last, lastAt: last.data.replace(/\s+$/, '').length };
  }

  /**
   * Selects without dragging: 'all'; 'toStart' and 'toEnd' from where the cursor (or the selection) is;
   * 'paragraph' for the one the cursor is in. Returns whether anything is selected.
   */
  function select(what) {
    var e = ends();
    var sel = window.getSelection();
    if (!e || !sel) return false;
    body.focus();
    var r = sel.rangeCount && body.contains(sel.anchorNode) ? sel.getRangeAt(0) : null;
    if (what === 'all') sel.setBaseAndExtent(e.first, e.firstAt, e.last, e.lastAt);
    else if (what === 'toEnd') sel.setBaseAndExtent(r ? r.startContainer : e.first, r ? r.startOffset : e.firstAt, e.last, e.lastAt);
    else if (what === 'toStart') sel.setBaseAndExtent(e.first, e.firstAt, r ? r.endContainer : e.last, r ? r.endOffset : e.lastAt);
    else if (what === 'paragraph') {
      var block = r ? blockOf(r.startContainer) : null;
      if (!block) return false;
      var pr = document.createRange();
      pr.selectNodeContents(block);
      sel.removeAllRanges();
      sel.addRange(pr);
    }
    reportState();
    return !sel.isCollapsed;
  }

  var blockTags = /^(p|div|h[1-6]|li|blockquote|pre|section|article|aside|header|footer|main|figure|figcaption|tr|dt|dd|ul|ol|dl|table|tbody|thead|tfoot|td|th|hr|address|center)$/;
  var skipTags = /^(script|style|head|title|meta|link|template|noscript|svg|math|iframe|object|img|picture|video|audio|canvas|button|input|select|textarea)$/;
  var keptTags = { i: 'i', em: 'em', b: 'b', strong: 'strong', sup: 'sup', sub: 'sub', u: 'u', s: 's', strike: 's', del: 's' };

  /**
   * What's under [node] as plain paragraphs: each a tag (p, or the heading it was), its words, and its words
   * with only italics, bold and raised or lowered letters kept. Everything else a page might carry is left out.
   */
  function blocksFrom(node) {
    var out = [];
    var open = [];
    var tag = 'p', html = '', text = '';
    function flush() {
      var h = html.replace(/(<br\/>|\s)+$/, '');
      for (var i = open.length - 1; i >= 0; i--) h += '</' + open[i] + '>';
      var t = text.replace(/\s+$/, '').replace(/^\s+/, '');
      if (t) out.push({ tag: tag, html: h.replace(/^\s+/, ''), text: t });
      html = ''; text = '';
      for (var j = 0; j < open.length; j++) html += '<' + open[j] + '>';
    }
    function words(s, pre) {
      s = s.replace(BAD, '');
      if (!pre) s = s.replace(/\s+/g, ' ');
      if (!pre && s === ' ' && (!text || /\s$/.test(text))) return;
      if (!pre && /^\s/.test(s) && (!text || /\s$/.test(text))) s = s.replace(/^\s+/, '');
      html += esc(s); text += s;
    }
    function walk(n, pre) {
      if (n.nodeType === 3 || n.nodeType === 4) { words(n.data, pre); return; }
      if (n.nodeType !== 1 && n.nodeType !== 11) return;
      var name = n.nodeType === 1 ? n.localName.toLowerCase() : '';
      if (skipTags.test(name)) return;
      if (name === 'br') {
        if (/<br\/>$/.test(html)) { html = html.replace(/<br\/>$/, ''); flush(); } else if (text) { html += '<br/>'; text += '\n'; }
        return;
      }
      var kept = null;
      if (name) {
        var style = n.getAttribute('style') || '';
        var notBold = /font-weight\s*:\s*(normal|[1-5]00)/i.test(style), notItalic = /font-style\s*:\s*normal/i.test(style);
        if (keptTags[name]) kept = (((name === 'b' || name === 'strong') && notBold) || ((name === 'i' || name === 'em') && notItalic)) ? null : keptTags[name];
        else if (/font-style\s*:\s*italic/i.test(style)) kept = 'i';
        else if (/font-weight\s*:\s*(bold|[6-9]00)/i.test(style)) kept = 'b';
      }
      var block = blockTags.test(name);
      if (block) { flush(); tag = /^h[1-6]$/.test(name) ? name : 'p'; }
      if (kept) { html += '<' + kept + '>'; open.push(kept); }
      for (var c = n.firstChild; c; c = c.nextSibling) walk(c, pre || name === 'pre');
      if (kept) { open.pop(); html += '</' + kept + '>'; }
      if (block) { flush(); tag = 'p'; }
    }
    walk(node, false);
    flush();
    // Tags left with nothing in them say nothing.
    for (var i = 0; i < out.length; i++) out[i].html = out[i].html.replace(/<(i|em|b|strong|sup|sub|u|s)><\/\1>/g, '');
    return out;
  }

  /** Plain text as paragraphs: at blank lines; or, with none, at each line unless the lines look cut to a width. */
  function blocksFromText(text) {
    text = String(text || '').replace(BAD, '').replace(/\r\n?/g, '\n').replace(/\u2028|\u2029/g, '\n');
    var parts;
    if (/\n[ \t]*\n/.test(text)) parts = text.split(/\n[ \t]*\n/).map(function (p) { return p.replace(/\s*\n\s*/g, ' '); });
    else {
      var lines = text.split('\n').map(function (l) { return l.trim(); }).filter(Boolean);
      var full = 0, open = 0;
      for (var i = 0; i < lines.length - 1; i++) {
        if (lines[i].length >= 45 && lines[i].length <= 100) full++;
        if (!/[.!?:;…"”’')\]]$/.test(lines[i])) open++;
      }
      var wrapped = lines.length > 2 && full >= (lines.length - 1) * 0.7 && open >= (lines.length - 1) * 0.5;
      parts = wrapped ? [lines.join(' ')] : lines;
    }
    return parts.map(function (p) { return p.trim(); }).filter(Boolean).map(function (p) { return { tag: 'p', html: esc(p), text: p }; });
  }

  function showCursor() {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return;
    var r = sel.getRangeAt(0).cloneRange();
    r.collapse(false);
    var rect = r.getClientRects()[0];
    if (!rect || (!rect.top && !rect.bottom)) {
      var el = r.startContainer.nodeType === 1 ? r.startContainer : r.startContainer.parentNode;
      rect = el && el.getBoundingClientRect ? el.getBoundingClientRect() : null;
    }
    if (!rect) return;
    if (rect.bottom > window.innerHeight - 48) window.scrollBy(0, rect.bottom - window.innerHeight * 0.6);
    else if (rect.top < 24) window.scrollBy(0, rect.top - window.innerHeight * 0.3);
  }

  /**
   * Puts copied words in where the cursor is (or over what's selected), as clean paragraphs. One paragraph
   * goes in as words in the line; several split the paragraph they land in, and the cursor ends after the last.
   * When what was copied ended with a paragraph break, the words after the cursor start a paragraph of their own.
   */
  function pasteIn(text, html) {
    var blocks = null;
    if (html) {
      try { blocks = blocksFrom(new DOMParser().parseFromString(html, 'text/html').body); } catch (e) { blocks = null; }
    }
    if (!blocks || !blocks.length) blocks = blocksFromText(text);
    if (!blocks.length) return false;
    body.focus();
    if (blocks.length === 1 && blocks[0].tag === 'p') {
      if (blocks[0].html === esc(blocks[0].text) && blocks[0].text.indexOf('\n') < 0) document.execCommand('insertText', false, blocks[0].text);
      else insertHtml(blocks[0].html);
    } else {
      var markup = blocks.map(function (b) { return '<' + b.tag + '>' + b.html + '</' + b.tag + '>'; }).join('');
      var sel = window.getSelection();
      var broken = /\n\s*$/.test(String(text || ''));
      var marker = null;
      if (broken && sel && sel.rangeCount && blockOf(sel.getRangeAt(0).endContainer)) {
        // The words after the cursor are given their own paragraph first, and the paste goes in before it.
        if (!sel.isCollapsed) document.execCommand('delete');
        var r0 = sel.getRangeAt(0);
        var blk = blockOf(r0.startContainer);
        var rest = document.createRange();
        rest.selectNodeContents(blk);
        rest.setStart(r0.startContainer, r0.startOffset);
        if (/\S/.test(rest.toString())) {
          document.execCommand('insertParagraph');
          marker = blockOf(sel.getRangeAt(0).startContainer);
          var prev = marker && marker.previousElementSibling;
          if (prev) {
            var back = document.createRange();
            back.selectNodeContents(prev);
            back.collapse(false);
            sel.removeAllRanges();
            sel.addRange(back);
          } else marker = null;
        }
      }
      insertHtml(markup);
      if (marker && marker.parentNode) {
        var to = document.createRange();
        to.selectNodeContents(marker);
        to.collapse(true);
        sel.removeAllRanges();
        sel.addRange(to);
      }
    }
    tidy();
    showCursor();
    schedule();
    reportState();
    return true;
  }

  /** What's selected, to copy: as text (a blank line between paragraphs) and as clean markup. With [cut], it's taken out. */
  function copySelection(cut) {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount || sel.isCollapsed) return '';
    var r = sel.getRangeAt(0);
    var blocks = blocksFrom(r.cloneContents());
    if (!blocks.length) return '';
    // Whole paragraphs (the selection ends where its paragraph does) end with a break, so they paste as paragraphs.
    var lastBlock = blockOf(r.endContainer), ended = false;
    if (lastBlock && blocks.length > 1) {
      var rest = document.createRange();
      rest.selectNodeContents(lastBlock);
      rest.setStart(r.endContainer, r.endOffset);
      ended = !/\S/.test(rest.toString());
    }
    var out = {
      text: blocks.map(function (b) { return b.text; }).join('\n\n') + (ended ? '\n' : ''),
      html: blocks.length === 1 && blocks[0].tag === 'p' ? blocks[0].html : blocks.map(function (b) { return '<' + b.tag + '>' + b.html + '</' + b.tag + '>'; }).join(''),
    };
    if (cut) {
      body.focus();
      document.execCommand('delete');
      schedule();
      reportState();
    }
    return JSON.stringify(out);
  }

  // ---- finding --------------------------------------------------------------------------------

  /** A range for one match: exactly where the app says, or else by counting the same text. */
  function rangeFor(m, nodes) {
    var n = nodes[m.segment];
    if (n && n.data.substr(m.offset, m.length) === m.text) {
      var r = document.createRange();
      r.setStart(n, m.offset);
      r.setEnd(n, m.offset + m.length);
      return r;
    }
    var k = -1;
    for (var i = 0; i < nodes.length; i++) {
      var at = -1;
      while ((at = nodes[i].data.indexOf(m.text, at + 1)) >= 0) {
        k++;
        if (k === (m.occurrence || 0)) {
          var r2 = document.createRange();
          r2.setStart(nodes[i], at);
          r2.setEnd(nodes[i], at + m.text.length);
          return r2;
        }
      }
    }
    return null;
  }

  function setHighlight(name, ranges) {
    try {
      if (!window.CSS || !CSS.highlights) return;
      if (!ranges.length) CSS.highlights.delete(name);
      else CSS.highlights.set(name, new Highlight(...ranges));
    } catch (e) {}
  }

  function showMatches(json) {
    var o = parse(json);
    var nodes = textNodes();
    var all = (o.all || []).map(function (m) { return rangeFor(m, nodes); }).filter(Boolean);
    setHighlight('kl-find', all);
    var cur = o.current ? rangeFor(o.current, nodes) : null;
    setHighlight('kl-current', cur ? [cur] : []);
    if (cur) {
      var rect = cur.getBoundingClientRect();
      if (rect.top < 80 || rect.bottom > window.innerHeight - 80) {
        window.scrollTo({ top: window.scrollY + rect.top - window.innerHeight / 3, behavior: o.smooth ? 'smooth' : 'auto' });
      }
    }
    return !!cur;
  }

  // ---- splitting -------------------------------------------------------------------------------

  /** The chapter's markup with the paragraph the cursor is in marked, so the app can split there. */
  function splitHere() {
    var sel = window.getSelection();
    var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
    if (!block) return '';
    // The outermost block that's still inside a single container, so the split falls between paragraphs.
    while (block.parentNode && block.parentNode !== body && blockNames.test(block.parentNode.localName) && block.parentNode.children.length === 1) {
      block = block.parentNode;
    }
    tidy();
    block.setAttribute('data-kl-split', '1');
    var s = serialize();
    block.removeAttribute('data-kl-split');
    return s;
  }

  // ---- start -----------------------------------------------------------------------------------

  function start() {
    body = document.body;
    if (!body) return;
    var st = parse(KLE.start());
    whole = !!st.whole;
    epub3 = st.epub3 !== false;
    root.classList.add('kl-edit');
    if (st.dark) root.classList.add('kl-dark');
    if (original) {
      var els = body.getElementsByTagName('*');
      for (var i = 0; i < els.length; i++) {
        original.add(els[i]);
        if (els[i].hasAttribute('style') || els[i].localName === 'font') ownSpans[spanKey(els[i])] = true;
      }
    }
    lastSent = serialize();
    body.setAttribute('contenteditable', 'true');
    body.setAttribute('spellcheck', st.spellcheck === false ? 'false' : 'true');
    try { document.execCommand('defaultParagraphSeparator', false, 'p'); } catch (e) {}
    try { document.execCommand('styleWithCSS', false, false); } catch (e) {}

    body.addEventListener('input', function () { schedule(); reportState(); });
    document.addEventListener('selectionchange', reportState);
    body.addEventListener('beforeinput', function (e) {
      if (e.inputType === 'historyUndo') { e.preventDefault(); send(); KLE.undo(); }
      else if (e.inputType === 'historyRedo') { e.preventDefault(); send(); KLE.redo(); }
    });
    body.addEventListener('keydown', function (e) {
      var mod = e.ctrlKey || e.metaKey;
      if (!mod) return;
      var key = e.key.toLowerCase();
      if (key === 'z' && !e.shiftKey) { e.preventDefault(); send(); KLE.undo(); }
      else if ((key === 'z' && e.shiftKey) || key === 'y') { e.preventDefault(); send(); KLE.redo(); }
    });
    // Pasted words come in clean: paragraphs, italics and bold, and nothing else of where they were copied from.
    body.addEventListener('paste', function (e) {
      e.preventDefault();
      var data = e.clipboardData;
      if (!data) return;
      pasteIn(data.getData('text/plain') || '', data.getData('text/html') || '');
    });
    body.addEventListener('drop', function (e) { e.preventDefault(); });
    body.addEventListener('click', function (e) {
      var img = e.target && e.target.closest ? e.target.closest('img') : null;
      selectImage(img);
      // Links are for editing here, not following.
      if (e.target && e.target.closest && e.target.closest('a')) e.preventDefault();
    });
    document.addEventListener('visibilitychange', function () { if (document.hidden) send(); });
    window.addEventListener('pagehide', function () { send(); });

    if (st.scrollY) window.scrollTo(0, st.scrollY);
    if (st.find) showMatches(JSON.stringify(st.find));
    try { KLE.ready(JSON.stringify({ whole: whole })); } catch (e) {}
    if (st.focus) body.focus();
  }

  window.KLED = {
    cmd: cmd,
    /** Sends anything typed but not yet sent; returns it too (or '' if nothing changed). */
    flush: function () {
      clearTimeout(timer);
      tidy();
      var s = serialize();
      if (s === lastSent) return '';
      lastSent = s;
      return s;
    },
    find: showMatches,
    clearFind: function () { setHighlight('kl-find', []); setHighlight('kl-current', []); },
    splitHere: splitHere,
    scrollY: function () { return window.scrollY; },
    deselect: function () { selectImage(null); },
    /** The words selected, to count. */
    selectedText: function () { var s = window.getSelection(); return s && s.rangeCount && !s.isCollapsed ? s.toString() : ''; },
    select: select,
    copy: copySelection,
    paste: pasteIn,
    /**
     * For the assistant: what's selected and the words before it, to work on or carry on from. Where they are
     * is remembered, for putBack, putRuns and addAfter.
     *
     * Words selected inside one paragraph come as text and markup (with `more` when there's a link, a picture
     * or styling among them that wouldn't survive being replaced). A selection over several paragraphs comes
     * as `runs`: the paragraphs it takes words from, whole, those side by side together, each as text with
     * *italics* and **bold** marked. Headings, and paragraphs holding more than words, are left out (`skipped`
     * counts them), so they stay as they are.
     */
    around: function () {
      var sel = window.getSelection();
      if (!sel || !sel.rangeCount) return '';
      var r = sel.getRangeAt(0);
      if (!body.contains(r.commonAncestorContainer) && r.commonAncestorContainer !== body) return '';
      aroundRange = r.cloneRange();
      aroundRuns = null;
      var lead = document.createRange();
      lead.selectNodeContents(body);
      lead.setEnd(r.startContainer, r.startOffset);
      var before = blocksFrom(lead.cloneContents()).map(function (b) { return b.text; }).join('\n\n');
      var out = { before: before.slice(-6000), text: '', html: '', more: false, skipped: 0 };
      var picked = sel.isCollapsed ? [] : selectedBlocks();
      if (picked.length > 1) {
        var runs = [], texts = [], last = null;
        for (var i = 0; i < picked.length; i++) {
          var el = picked[i];
          var marked = /^(p|li|blockquote|div|dd)$/i.test(el.localName) && wordsOnly(holderOf(el)) ? markedOf(holderOf(el)).replace(/[ \t]*\n[ \t]*/g, '\n').trim() : '';
          if (!marked) {
            if (/\S/.test(el.textContent) || el.querySelector('img,svg')) out.skipped++;
            last = null;
            continue;
          }
          if (last && last.nextElementSibling === el && last.localName === el.localName) { runs[runs.length - 1].push(el); texts[texts.length - 1].push(marked); }
          else { runs.push([el]); texts.push([marked]); }
          last = el;
        }
        aroundRuns = runs;
        out.runs = texts;
        out.text = texts.map(function (t) { return t.join('\n\n'); }).join('\n\n');
      } else if (!sel.isCollapsed) {
        var cut = r.cloneContents();
        var blocks = blocksFrom(cut);
        out.text = blocks.map(function (b) { return b.text; }).join('\n\n');
        out.html = blocks.map(function (b) { return '<' + b.tag + '>' + b.html + '</' + b.tag + '>'; }).join('');
        out.more = !wordsOnly(holderOf(cut));
      }
      return JSON.stringify(out);
    },
    /** Puts words in where around() last looked (in place of what was selected there), as pasting would. */
    putBack: function (text, html) {
      if (aroundRange) {
        try {
          var sel = window.getSelection();
          sel.removeAllRanges();
          sel.addRange(aroundRange);
        } catch (e) {}
      }
      body.focus();
      return pasteIn(text, html);
    },
    /**
     * Puts the assistant's paragraphs in place of those around() gave it. Each edit is {run, from, count, html}:
     * `count` paragraphs of a run, from the one at `from`, become the paragraphs in `html`. Each keeps being
     * the paragraph it was (its class, its place); when more come back than went, the extra ones are made
     * like the last, and when fewer do, the ones left over go. Returns how many paragraphs it changed.
     */
    putRuns: function (json) {
      var edits = [];
      try { edits = JSON.parse(json) || []; } catch (e) { edits = []; }
      if (!aroundRuns) return 0;
      var changed = 0, end = null;
      for (var x = 0; x < edits.length; x++) {
        var e = edits[x], run = aroundRuns[e.run];
        if (!run || !e.html || !e.html.length) continue;
        var els = run.slice(e.from, e.from + e.count);
        if (els.length !== e.count || els.some(function (el) { return !el.parentNode || !body.contains(el); })) continue;
        var n = Math.min(els.length, e.html.length), i;
        for (i = 0; i < n; i++) if (setWords(els[i], e.html[i])) changed++;
        var lastEl = els[n - 1];
        for (i = n; i < e.html.length; i++) {
          var extra = likeThis(lastEl);
          if (!setWords(extra, e.html[i])) continue;
          lastEl.parentNode.insertBefore(extra, lastEl.nextSibling);
          lastEl = extra;
          changed++;
        }
        for (i = n; i < els.length; i++) { els[i].parentNode.removeChild(els[i]); changed++; }
        end = lastEl;
      }
      aroundRuns = null;
      aroundRange = null;
      if (changed) {
        if (end && end.parentNode) cursorAtEndOf(end);
        tidy();
        schedule();
        reportState();
      }
      return changed;
    },
    /**
     * Puts new writing in after where around() last looked. After a finished paragraph it starts new ones,
     * made like that one; anywhere else its first paragraph carries on the line the cursor is in.
     */
    addAfter: function (text, html) {
      var sel = window.getSelection();
      if (aroundRange) {
        try {
          var at = aroundRange.cloneRange();
          at.collapse(false);
          sel.removeAllRanges();
          sel.addRange(at);
        } catch (e) {}
      }
      aroundRange = null;
      aroundRuns = null;
      body.focus();
      var r = sel && sel.rangeCount ? sel.getRangeAt(0) : null;
      var blk = r ? blockOf(r.startContainer) : null;
      if (!blk) return pasteIn(text, html);
      var rest = document.createRange();
      rest.selectNodeContents(blk);
      rest.setStart(r.startContainer, r.startOffset);
      var led = document.createRange();
      led.selectNodeContents(blk);
      led.setEnd(r.startContainer, r.startOffset);
      var said = led.toString();
      if (!/\S/.test(rest.toString()) && /[.!?…:"”’'»)\]*]\s*$/.test(said)) {
        var blocks = null;
        if (html) { try { blocks = blocksFrom(new DOMParser().parseFromString(html, 'text/html').body); } catch (e) { blocks = null; } }
        if (!blocks || !blocks.length) blocks = blocksFromText(text);
        if (!blocks.length) return false;
        var after = blk;
        for (var i = 0; i < blocks.length; i++) {
          var el = /^(p|li)$/i.test(blk.localName) ? likeThis(blk) : document.createElementNS(blk.namespaceURI, 'p');
          if (!setWords(el, blocks[i].html)) continue;
          after.parentNode.insertBefore(el, after.nextSibling);
          after = el;
        }
        cursorAtEndOf(after);
        tidy();
        showCursor();
        schedule();
        reportState();
        return true;
      }
      if (/\S$/.test(said) && !/^[\s.,;:!?…)\]”’]/.test(String(text || ''))) document.execCommand('insertText', false, ' ');
      return pasteIn(text, html);
    },
  };
  var aroundRange = null;
  var aroundRuns = null;

  // ---- for the assistant ------------------------------------------------------------------------

  var wordTags = /^(i|em|b|strong|br)$/i;

  /** The element holding a paragraph's words: the paragraph, or the box all its words are wrapped in. */
  function holderOf(el) {
    for (;;) {
      var kids = [];
      for (var n = el.firstChild; n; n = n.nextSibling) {
        if (n.nodeType === 8 || (n.nodeType === 3 && !/\S/.test(n.data))) continue;
        kids.push(n);
      }
      if (kids.length !== 1 || kids[0].nodeType !== 1 || wordTags.test(kids[0].localName) || !kids[0].firstChild) return el;
      el = kids[0];
    }
  }

  /** Whether there's nothing in [node] but words, italics, bold and line breaks. */
  function wordsOnly(node) {
    for (var n = node.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3 || n.nodeType === 4 || n.nodeType === 8) continue;
      if (n.nodeType !== 1) return false;
      var name = n.localName.toLowerCase();
      if (name === 'br') continue;
      if (!(wordTags.test(name) || (name === 'span' && !n.attributes.length))) return false;
      if (!wordsOnly(n)) return false;
    }
    return true;
  }

  /** Words for the assistant: *italics* and **bold** in asterisks, a line break as a new line. */
  function markedOf(node) {
    var out = '';
    for (var n = node.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3 || n.nodeType === 4) { out += n.data.replace(BAD, '').replace(/\s+/g, ' '); continue; }
      if (n.nodeType !== 1) continue;
      var name = n.localName.toLowerCase();
      if (name === 'br') { out += '\n'; continue; }
      var inner = markedOf(n);
      var mark = (name === 'b' || name === 'strong') ? '**' : (name === 'i' || name === 'em') ? '*' : '';
      // The marks hug the words: spaces at the ends stay outside them.
      if (mark && /\S/.test(inner)) inner = inner.replace(/^(\s*)([\s\S]*?)(\s*)$/, function (_, a, b, c) { return a + mark + b + mark + c; });
      out += inner;
    }
    return out;
  }

  /** Gives a paragraph new words (markup with italics, bold and line breaks), leaving the paragraph itself as it is. */
  function setWords(el, html) {
    var blocks = [];
    try { blocks = blocksFrom(new DOMParser().parseFromString(String(html || ''), 'text/html').body); } catch (e) { blocks = []; }
    var inner = blocks.map(function (b) { return b.html; }).join('<br/>');
    if (!inner) return false;
    var holder = holderOf(el);
    try {
      holder.innerHTML = inner;
    } catch (e) {
      holder.textContent = blocks.map(function (b) { return b.text; }).join('\n');
    }
    return true;
  }

  /** A new, empty-handed paragraph made like [el]: its class and the boxes round its words, none of its ids. */
  function likeThis(el) {
    var copy = el.cloneNode(true);
    copy.removeAttribute('id');
    var ids = copy.querySelectorAll('[id]');
    for (var i = 0; i < ids.length; i++) ids[i].removeAttribute('id');
    return copy;
  }

  function cursorAtEndOf(el) {
    try {
      var sel = window.getSelection();
      var r = document.createRange();
      r.selectNodeContents(holderOf(el));
      r.collapse(false);
      sel.removeAllRanges();
      sel.addRange(r);
    } catch (e) {}
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
