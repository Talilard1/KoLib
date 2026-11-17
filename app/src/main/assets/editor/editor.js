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
  var original = typeof WeakSet !== 'undefined' ? new WeakSet() : null;
  /** The book's own styled spans, by what they say: a copy the browser makes of one (splitting a paragraph) is the book's too. */
  var ownSpans = {};
  var XHTML = 'http://www.w3.org/1999/xhtml';
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
    var els = body.querySelectorAll('i[style],b[style],em[style],strong[style],sup[style],sub[style]');
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
      var st = {
        bold: !wide && safeState('bold'),
        italic: !wide && safeState('italic'),
        block: block ? block.localName.toLowerCase() : null,
        align: alignOf(block),
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
    body.focus();
    switch (name) {
      case 'bold': document.execCommand('bold'); break;
      case 'italic': document.execCommand('italic'); break;
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
  var keptTags = { i: 'i', em: 'em', b: 'b', strong: 'strong', sup: 'sup', sub: 'sub' };

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
    for (var i = 0; i < out.length; i++) out[i].html = out[i].html.replace(/<(i|em|b|strong|sup|sub)><\/\1>/g, '');
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
    select: select,
    copy: copySelection,
    paste: pasteIn,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
