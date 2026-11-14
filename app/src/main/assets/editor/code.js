/*
 * The "Show code" view: the chapter's XHTML as plain text to edit, coloured with the browser's
 * highlight ranges (so the text itself stays one plain string). window.KLC is the app's side.
 */
(function () {
  'use strict';
  var KLC = window.KLC || { start: function () { return '{}'; }, changed: function () {}, ready: function () {} };
  var pre, bar, msg, timer = 0, colourTimer = 0, last = '';

  function text() {
    // The browser may break the text into pieces as it's edited; it's read back as one.
    return pre.innerText.replace(/\r\n?/g, '\n').replace(/\n$/, '');
  }

  function nodeAt(offset) {
    var w = document.createTreeWalker(pre, NodeFilter.SHOW_TEXT, null);
    var n, at = 0;
    while ((n = w.nextNode())) {
      if (offset <= at + n.data.length) return [n, offset - at];
      at += n.data.length;
    }
    return null;
  }

  function range(start, end) {
    var a = nodeAt(start), b = nodeAt(end);
    if (!a || !b) return null;
    var r = document.createRange();
    r.setStart(a[0], a[1]);
    r.setEnd(b[0], b[1]);
    return r;
  }

  var patterns = [
    ['c-comment', /<!--[\s\S]*?-->/g],
    ['c-tag', /<\/?[A-Za-z][\w:.-]*|\/?>|<\?[\s\S]*?\?>|<!DOCTYPE[^>]*>/g],
    ['c-attr', /\s[A-Za-z_:][\w:.-]*(?=\s*=)/g],
    ['c-value', /=\s*("[^"]*"|'[^']*')/g],
    ['c-entity', /&#?\w+;/g],
  ];

  function colour() {
    if (!window.CSS || !CSS.highlights) return;
    pre.normalize();
    var t = pre.firstChild && pre.firstChild.nodeType === 3 && pre.childNodes.length === 1 ? pre.firstChild.data : null;
    for (var i = 0; i < patterns.length; i++) {
      var name = patterns[i][0], re = patterns[i][1];
      var ranges = [];
      var src = t != null ? t : text();
      re.lastIndex = 0;
      var m, count = 0;
      while ((m = re.exec(src)) && count < 40000) {
        var start = m.index, end = m.index + m[0].length;
        if (name === 'c-value') start += m[0].indexOf(m[1]);
        var r;
        if (t != null) {
          r = document.createRange();
          r.setStart(pre.firstChild, start);
          r.setEnd(pre.firstChild, end);
        } else r = range(start, end);
        if (r) ranges.push(r);
        count++;
      }
      CSS.highlights.set(name, new Highlight(...ranges));
    }
  }

  function lineStart(src, line) {
    var at = 0;
    for (var i = 1; i < line && at >= 0; i++) at = src.indexOf('\n', at) + 1;
    return Math.max(0, at);
  }

  function showError(e) {
    if (!e || !e.message) {
      bar.hidden = true;
      CSS.highlights && CSS.highlights.delete('c-error');
      return;
    }
    msg.textContent = e.text || ('Line ' + e.line + ': ' + e.message + '. Your change won\'t be saved to the chapter until this is fixed.');
    bar.hidden = false;
    var src = text();
    var s = lineStart(src, e.line);
    var end = src.indexOf('\n', s);
    var r = range(s, end < 0 ? src.length : end);
    if (r && CSS.highlights) CSS.highlights.set('c-error', new Highlight(r));
    bar.onclick = function () { goToLine(e.line); };
  }

  function goToLine(line) {
    var src = text();
    var r = range(lineStart(src, line), lineStart(src, line));
    if (!r) return;
    var rect = r.getBoundingClientRect();
    window.scrollTo(0, window.scrollY + rect.top - window.innerHeight / 3);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
  }

  function send() {
    clearTimeout(timer);
    var t = text();
    if (t === last) return '';
    last = t;
    KLC.changed(t);
    return t;
  }

  function start() {
    pre = document.getElementById('code');
    bar = document.getElementById('bar');
    msg = document.getElementById('msg');
    var st = {};
    try { st = JSON.parse(KLC.start() || '{}'); } catch (e) {}
    if (st.dark) document.documentElement.classList.add('dark');
    pre.textContent = st.text || '';
    last = text();
    colour();
    showError(st.error);
    pre.addEventListener('input', function () {
      clearTimeout(timer);
      timer = setTimeout(send, 600);
      clearTimeout(colourTimer);
      colourTimer = setTimeout(colour, 250);
    });
    pre.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') e.stopPropagation();
    });
    if (st.line) goToLine(st.line);
    else if (st.scrollY) window.scrollTo(0, st.scrollY);
    KLC.ready();
  }

  window.KLCD = {
    flush: function () { return send(); },
    error: function (json) { var e = null; try { e = JSON.parse(json); } catch (x) {} showError(e); },
    text: function () { return text(); },
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
