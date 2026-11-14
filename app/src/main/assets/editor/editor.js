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
  var XHTML = 'http://www.w3.org/1999/xhtml';

  function parse(s) { try { return JSON.parse(s || '{}') || {}; } catch (e) { return {}; } }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

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

  /** Spans the browser makes to keep a look when paragraphs are joined; the book never had them. */
  function unwrapBrowserSpans() {
    if (!original) return;
    var spans = body.querySelectorAll('span[style], font');
    for (var i = 0; i < spans.length; i++) {
      var s = spans[i];
      if (original.has(s)) continue;
      var p = s.parentNode;
      while (s.firstChild) p.insertBefore(s.firstChild, s);
      p.removeChild(s);
    }
  }

  function serialize() {
    var ser = new XMLSerializer();
    var copy = cleanCopy();
    if (whole) return ser.serializeToString(copy);
    var out = '';
    for (var n = copy.firstChild; n; n = n.nextSibling) out += ser.serializeToString(n);
    return out;
  }

  function send() {
    clearTimeout(timer);
    timer = 0;
    unwrapBrowserSpans();
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

  function blockOf(node) {
    while (node && node !== body) {
      if (node.nodeType === 1 && blockNames.test(node.localName)) return node;
      node = node.parentNode;
    }
    return null;
  }

  function reportState() {
    clearTimeout(stateTimer);
    stateTimer = setTimeout(function () {
      var sel = window.getSelection();
      var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
      var st = {
        bold: safeState('bold'),
        italic: safeState('italic'),
        block: block ? block.localName.toLowerCase() : null,
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
      // A fallback for when the browser won't: put it after the current block.
      var sel = window.getSelection();
      var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
      var holder = document.createElementNS(XHTML, 'div');
      holder.innerHTML = html;
      var after = block ? block : body.lastChild;
      while (holder.firstChild) {
        if (after && after.parentNode) after.parentNode.insertBefore(holder.firstChild, after.nextSibling);
        else body.appendChild(holder.firstChild);
      }
    }
  }

  /** Puts a new block (a scene break, a picture) after the paragraph the cursor is in. */
  function insertBlock(el) {
    var sel = window.getSelection();
    var block = sel && sel.rangeCount ? blockOf(sel.anchorNode) : null;
    while (block && block.parentNode !== body && block.parentNode && !blockNames.test(block.parentNode.localName)) block = block.parentNode;
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

  function cmd(name, arg) {
    if (!body) return;
    body.focus();
    switch (name) {
      case 'bold': document.execCommand('bold'); break;
      case 'italic': document.execCommand('italic'); break;
      case 'block': document.execCommand('formatBlock', false, arg || 'p'); break;
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

  // ---- finding --------------------------------------------------------------------------------

  function textNodes() {
    var out = [];
    var w = document.createTreeWalker(body, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_CDATA_SECTION, null);
    for (var n = w.nextNode(); n; n = w.nextNode()) out.push(n);
    return out;
  }

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
    unwrapBrowserSpans();
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
      for (var i = 0; i < els.length; i++) original.add(els[i]);
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
    // Pasted text comes in plain, as paragraphs.
    body.addEventListener('paste', function (e) {
      e.preventDefault();
      var text = (e.clipboardData && e.clipboardData.getData('text/plain')) || '';
      if (!text) return;
      var paras = text.replace(/\r\n?/g, '\n').split(/\n\s*\n/).map(function (p) { return p.replace(/\s*\n\s*/g, ' ').trim(); }).filter(Boolean);
      if (paras.length > 1) insertHtml(paras.map(function (p) { return '<p>' + esc(p) + '</p>'; }).join(''));
      else document.execCommand('insertText', false, paras[0] || text);
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
      unwrapBrowserSpans();
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
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
