/*
 * KoLib's reader, inside one chapter of a book.
 *
 * The chapter is laid out in CSS columns, one (or two, side by side) per screen, and turned like
 * pages; or, when scrolling, as one long page read from top to bottom. Taps, swipes and keys move
 * through it here; the app is told where the reader is, and asked to open the next or previous
 * chapter at the edges. Autoscroll moves on by itself, at a reading speed. window.KL is the app's side.
 */
(function () {
  'use strict';

  var KL = window.KL || {
    start: function () { return '{}'; }, ready: function () {}, position: function () {}, edge: function () {},
    tap: function () {}, link: function () {}, note: function () {}, image: function () {}, external: function () {},
    jumped: function () {}, log: function () {}, turn: function () {}, selecting: function () {}, annotation: function () {},
    counterReady: function () {},
  };
  var root = document.documentElement;
  var scroller = document.scrollingElement || root;
  var settings = {};
  var W = 0, H = 0, page = 0, pages = 1, padX = 48, padY = 44, perScreen = 1;
  // The pages of text in the chapter: as many as its screens, or with two side by side, up to twice as
  // many (the last screen can have text on its left-hand page only). Each is numbered on its own.
  var columns = 1, colW = 0;
  // The space above and below the text: room is kept there for what the app shows in the margins.
  var padTop = 44, padBottom = 44;
  // A chapter that's only a picture (a cover, a plate): no page headings over it.
  var picturePage = false;
  // Two pages side by side: a chapter of one page sits on the right-hand page, as in a printed book, with a
  // blank page before it (blankBefore); a wide picture alone (a map) goes across both pages (spreadPicture).
  var blankBefore = 0, spreadPicture = false;
  // Two pages side by side, each just a picture (plates one after another, put together by the app): [left, right].
  var picturePair = null;
  // Pictures set where they go on the screen, into the margins (see placePicture), to be put back before laying out again.
  var placedPics = [];
  // A page of its own kind that a printed book doesn't number: a dedication, an epigraph or other quote, a
  // part's title, a map or plate. No page headings over it.
  var displayPage = false;
  // Where the app asks for a chapter of one page to go, two pages side by side: 'cover' or 'title' (a book's
  // title page) in the middle of the screen, 'left' (a dedication), or as it lays out (on the right).
  var place = null;
  function centred() { return place === 'cover' || place === 'title' || place === 'centre'; }
  // The chapter starts one of the book's chapters (the app says), rather than carrying one on: its number
  // and name can be set as print has them, its first letter as a drop cap, and its first page on the right.
  var chapterOpening = true;
  // Two short pages from files of their own, shown side by side: the app puts the left one in a box in
  // front of the right one's body.
  function pairBox() { return document.getElementById('kl-pair'); }
  var ready = false, animating = 0, laidOutWidth = 0;
  // Only counting the chapter's pages, in a WebView that's never shown (see countOnly).
  var countingOnly = false;
  // Counting one chapter after another in the one page (see countInShell): the chapter's own address,
  // for what its file name says. Null in a chapter opened on its own.
  var pagePath = null;
  var startTarget = null;
  // Scrolling: the chapter as one long page. The bands at the top and bottom are where the app shows
  // the margins' chapter title, time and so on, over the text going by.
  var scrolled = false, bandTop = 0, bandBottom = 0, lineH = 24;
  // The chapters either side, for the end of a scrolled chapter.
  var nextTitle = null, isLast = false;

  function parse(s) { try { return JSON.parse(s || '{}') || {}; } catch (e) { return {}; } }
  function log(m) { try { KL.log(String(m)); } catch (e) {} }

  // ---- settings -------------------------------------------------------------------------------

  function setVar(name, value) { root.style.setProperty(name, value); }

  function applySettings(s) {
    settings = s || {};
    scrolled = s.flow === 'scroll';
    root.classList.add('kl-paged');
    root.classList.toggle('kl-scroll', scrolled);
    bindTouchMove();
    applyColours(s);
    setVar('--kl-font-size', Math.round((s.fontScale || 1) * 100) + '%');
    if (s.font) { setVar('--kl-font', s.font); root.classList.add('kl-font'); } else root.classList.remove('kl-font');
    root.classList.toggle('kl-plain-links', !!s.plainLinks);
    root.classList.toggle('kl-print-heads', !!s.printHeads);
    root.classList.toggle('kl-dropcaps', !!s.dropCaps);
    if (s.line) { setVar('--kl-line', String(s.line)); root.classList.add('kl-lh'); } else root.classList.remove('kl-lh');
    root.classList.toggle('kl-hyphens', s.hyphens === 'on');
    root.classList.toggle('kl-nohyphens', s.hyphens === 'off');
    if (s.hyphens === 'on' && !root.getAttribute('lang') && !root.getAttributeNS('http://www.w3.org/XML/1998/namespace', 'lang')) {
      root.setAttribute('lang', s.lang || 'en');
    }
    document.body && alignParagraphs(s.align || 'book');
    document.body && blendPictures();
    document.body && letPaperGo();
    measure();
  }

  /** The page's colours. Nothing moves: the text is laid out just as it was. */
  function applyColours(s) {
    setVar('--kl-bg', s.bg || '#fbf8f1');
    setVar('--kl-fg', s.fg || '#1f1c18');
    setVar('--kl-link', s.link || '#1d4f91');
    setVar('--kl-bar', s.barColor || s.fg || '#1f1c18');
    if (s.sel) setVar('--kl-sel', s.sel);
    if (s.hl) setVar('--kl-hl', s.hl);
    root.classList.toggle('kl-recolor', !!s.recolor);
    root.classList.toggle('kl-dim-images', !!s.dimImages);
    highlightStyles();
  }

  // ---- a book's white paper --------------------------------------------------------------------

  /**
   * Books made in a word processor often give their paragraphs a white background (the paper they were typed on),
   * which shows as a white box behind each one on a page that isn't white. Such a white (or nearly white) background
   * on the book's text is let go, so the page's own colour shows through. A coloured box (a note, a sidebar) keeps
   * its colour, and so does anything inside one; a white mat round a picture stays. (Dark and tinted pages clear
   * every background anyway: see kl-recolor.)
   */
  function letPaperGo() {
    if (countingOnly) return;
    var roots = [document.body, document.getElementById('kl-opening'), pairBox()];
    // All read first, then marked: a mark changes styles, and reading after each would work them all out again.
    var paper = [];
    for (var i = 0; i < roots.length; i++) if (roots[i]) letPaperGoIn(roots[i], paper);
    for (var j = 0; j < paper.length; j++) paper[j].classList.add('kl-paper');
  }

  /** 'none' (no background), 'paper' (white or nearly white) or 'colour'. */
  function backgroundKind(style) {
    if (style.backgroundImage && style.backgroundImage !== 'none') return 'colour';
    var m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+%?))?\s*\)/.exec(style.backgroundColor || '');
    if (!m) return 'none';
    var a = m[4] === undefined ? 1 : (/%$/.test(m[4]) ? parseFloat(m[4]) / 100 : parseFloat(m[4]));
    if (!(a > 0.04)) return 'none';
    var r = +m[1], g = +m[2], b = +m[3];
    return Math.min(r, g, b) >= 236 && Math.max(r, g, b) - Math.min(r, g, b) <= 20 ? 'paper' : 'colour';
  }

  /** Finds in [box] the white boxes ([paper]) to take away, but not in a coloured box, nor a picture's white mat. */
  function letPaperGoIn(box, paper) {
    for (var el = box.firstElementChild; el; el = el.nextElementSibling) {
      var name = (el.localName || '').toLowerCase();
      if (/^(img|svg|video|picture|canvas|math|iframe|object|script|style|template)$/.test(name)) continue;
      var kind = backgroundKind(window.getComputedStyle(el));
      if (kind === 'colour') continue;
      if (kind === 'paper') {
        // A picture on its white mat keeps its mat.
        if (!/\S/.test(el.textContent || '') && el.querySelector('img, svg, image, video, canvas')) continue;
        paper.push(el);
      }
      letPaperGoIn(el, paper);
    }
  }

  // ---- pictures that take the page's colour ------------------------------------------------------

  /**
   * Black-and-white pictures (a chapter's ornament or banner, a drawn initial, a timeline) are ink on
   * white paper, which shows as a white box on a tinted or dark page. Blended, their white takes the
   * page's colour; on a dark page they're turned over first, so the ink is light and the paper dark.
   * 'ornaments' blends pictures that are ink on paper, but not plates (pictures in a ruled frame, which
   * keep their paper); 'all' every picture; 'off' none.
   */
  var inkOnPaperKnown = typeof WeakMap !== 'undefined' ? new WeakMap() : null;

  function blendPictures() {
    if (countingOnly) return;
    var mode = settings.blend || 'ornaments';
    var pics = document.querySelectorAll('img, svg');
    for (var i = 0; i < pics.length; i++) markBlend(pics[i], mode);
  }

  function markBlend(el, mode) {
    if (mode === 'off') { el.classList.remove('kl-blend'); return; }
    if (mode === 'all') { el.classList.add('kl-blend'); return; }
    isInkOnPaper(el, function (yes) {
      el.classList.toggle('kl-blend', yes && (settings.blend || 'ornaments') === 'ornaments');
    });
  }

  /** Calls back with whether a picture is ink on paper (see [inkOnPaper]) and not a framed plate. */
  function isInkOnPaper(el, then) {
    var known = inkOnPaperKnown ? inkOnPaperKnown.get(el) : undefined;
    if (known !== undefined) { then(known); return; }
    var src = pictureSource(el);
    if (!src) { then(false); return; }
    var img = new Image();
    img.onload = function () {
      var yes = false;
      try { yes = inkOnPaper(img) && !framed(img); } catch (e) { log(e); }
      if (inkOnPaperKnown) inkOnPaperKnown.set(el, yes);
      then(yes);
    };
    img.onerror = function () { then(false); };
    img.src = src;
  }

  function pictureSource(el) {
    if (el.localName === 'img') return el.currentSrc || el.src || null;
    // An svg holding a picture.
    var im = el.querySelector ? el.querySelector('image') : null;
    if (!im) return null;
    var href = im.getAttribute('href') || im.getAttributeNS('http://www.w3.org/1999/xlink', 'href');
    if (!href) return null;
    try { return new URL(href, document.baseURI).href; } catch (e) { return null; }
  }

  /** The picture's pixels, drawn no bigger than [most] across; [sharp]: each one picked, not averaged. */
  function pixels(img, most, sharp) {
    var w = img.naturalWidth, h = img.naturalHeight;
    var k = Math.min(1, most / Math.max(w, h));
    var cw = Math.max(1, Math.round(w * k)), ch = Math.max(1, Math.round(h * k));
    var c = document.createElement('canvas');
    c.width = cw;
    c.height = ch;
    var g = c.getContext('2d');
    if (sharp) g.imageSmoothingEnabled = false;
    g.drawImage(img, 0, 0, cw, ch);
    return { d: g.getImageData(0, 0, cw, ch).data, w: cw, h: ch };
  }

  /** How much of a small copy of the picture is in colour, grey (and in which shades) and white. */
  function tones(img, sharp) {
    var d = pixels(img, 96, sharp).d;
    var t = { seen: 0, colour: 0, grey: 0, white: 0, shades: [] };
    for (var b = 0; b < 52; b++) t.shades.push(0);
    for (var i = 0; i < d.length; i += 4) {
      if (d[i + 3] < 40) continue; // see-through: already the page
      var mx = Math.max(d[i], d[i + 1], d[i + 2]), mn = Math.min(d[i], d[i + 1], d[i + 2]);
      t.seen++;
      if (mx - mn > 48) t.colour++;
      var l = (mx + mn) / 2;
      if (l > 200) t.white++;
      else if (l > 70) {
        t.grey++;
        t.shades[Math.floor(l / 5)]++;
      }
    }
    return t;
  }

  /**
   * Ink on paper: next to no colour, and either few greys (black lines and lettering on white) or greys
   * that are one flat shade (a grey banner or shape), rather than the many greys of a photograph. A
   * busy chart (small lettering, thin lines) turns grey when shrunk, so it's looked at sharp too: there
   * it's black and white, with plenty of paper.
   */
  function inkOnPaper(img) {
    if (!img.naturalWidth || !img.naturalHeight) return false;
    var t = tones(img, false);
    if (!t.seen || t.colour / t.seen >= 0.04) return false;
    if (t.grey / t.seen < 0.3) return true;
    // Plenty of grey: all right if nearly all of it is one shade (within about 15 levels).
    var most = 0;
    for (var s = 0; s < t.shades.length - 2; s++) most = Math.max(most, t.shades[s] + t.shades[s + 1] + t.shades[s + 2]);
    if (most / t.grey >= 0.7) return true;
    var sharp = tones(img, true);
    return sharp.seen > 0 && sharp.colour / sharp.seen < 0.04 && sharp.grey / sharp.seen < 0.25 && sharp.white / sharp.seen >= 0.6;
  }

  /**
   * A plate: a picture in a ruled frame (a line along all four edges), like the engravings in an old
   * illustrated book. Its paper is part of the picture, so it isn't blended. Each edge is looked at as
   * a narrow strip, so a frame drawn a little crooked, or scanned that way, is still found.
   */
  function framed(img) {
    if (!img.naturalWidth || !img.naturalHeight) return false;
    var p = pixels(img, 400), d = p.d, w = p.w, h = p.h;
    if (w < 24 || h < 24) return false;
    function ink(x, y) {
      var i = (y * w + x) * 4;
      if (d[i + 3] < 40) return false;
      return (Math.max(d[i], d[i + 1], d[i + 2]) + Math.min(d[i], d[i + 1], d[i + 2])) / 2 < 200;
    }
    var bx = Math.max(3, Math.round(w * 0.06)), by = Math.max(3, Math.round(h * 0.06));
    /** Ink in the strip along most of an edge: [along] the edge's length, [across] the strip's width. */
    function lined(along, across, at) {
      var n = 0;
      for (var a = 0; a < along; a++) {
        for (var c = 0; c < across; c++) if (at(a, c)) { n++; break; }
      }
      return n >= along * 0.85;
    }
    return lined(w, by, function (x, y) { return ink(x, y); }) &&
      lined(w, by, function (x, y) { return ink(x, h - 1 - y); }) &&
      lined(h, bx, function (y, x) { return ink(x, y); }) &&
      lined(h, bx, function (y, x) { return ink(w - 1 - x, y); });
  }

  /** Paragraphs the book aligns to the left or justifies follow the reader's choice; centred ones stay centred. */
  function alignParagraphs(align) {
    var els = document.body.querySelectorAll('p, li, dd, blockquote, div');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      el.classList.remove('kl-align-left', 'kl-align-justify');
    }
    if (align !== 'left' && align !== 'justify') return;
    var cls = align === 'left' ? 'kl-align-left' : 'kl-align-justify';
    for (var j = 0; j < els.length; j++) {
      var e = els[j];
      if (e.tagName.toLowerCase() === 'div' && !hasOwnText(e)) continue;
      var a = getComputedStyle(e).textAlign;
      if (a === 'start' || a === 'left' || a === 'justify' || a === '-webkit-auto' || a === 'auto') e.classList.add(cls);
    }
  }

  function hasOwnText(e) {
    for (var n = e.firstChild; n; n = n.nextSibling) if (n.nodeType === 3 && n.data.trim()) return true;
    return false;
  }

  /** The screen's size in CSS pixels; 0 until the WebView has been given its place. */
  function screenWidth() { return window.innerWidth || root.clientWidth || 0; }
  function screenHeight() { return window.innerHeight || root.clientHeight || 0; }
  function hasSize() { return screenWidth() > 0 && screenHeight() > 0; }

  function measure() {
    W = screenWidth();
    H = screenHeight();
    // Pixels, not 100vw/100vh: a WebView can report viewport units as zero.
    setVar('--kl-vw', W + 'px');
    setVar('--kl-vh', H + 'px');
    var s = settings;
    if (scrolled) {
      // One column, no wider than is easy to read: the wider the margins chosen, the narrower it is.
      perScreen = 1;
      padX = Math.round(Math.max(16, Math.min(140, W * (s.marginFrac || 0.06))));
      padY = Math.round(Math.max(20, Math.min(48, H * 0.035)));
      bandTop = Math.max(0, s.bandTop || 0);
      bandBottom = Math.max(0, s.bandBottom || 0);
      var f = s.marginFrac || 0.06;
      setVar('--kl-measure', f < 0.05 ? 'none' : f < 0.09 ? '46em' : '38em');
      setVar('--kl-pad-x', padX + 'px');
      setVar('--kl-pad-y', padY + 'px');
      setVar('--kl-band-top', bandTop + 'px');
      setVar('--kl-band-bottom', bandBottom + 'px');
      setVar('--kl-page-h', Math.max(80, H - bandTop - bandBottom - 2 * padY) + 'px');
      padTop = padBottom = padY;
      colW = Math.min(W - 2 * padX, W);
      return;
    }
    bandTop = bandBottom = 0;
    // Two pages side by side on a wide screen held sideways, unless the reader says otherwise.
    perScreen = s.columns === 2 ? 2 : s.columns === 1 ? 1 : (W > H && W >= 880 ? 2 : 1);
    var pageWidth = W / perScreen;
    var margin = Math.round(Math.max(16, Math.min(140, pageWidth * (s.marginFrac || 0.06))));
    padX = margin;
    padY = s.marginY || Math.round(Math.max(28, Math.min(64, H * 0.05)));
    // At least the room the app asks for, above and below, for the page headings and the rest.
    padTop = Math.max(padY, Math.round(s.minTop || 0));
    padBottom = Math.max(padY, Math.round(s.minBottom || 0));
    var col = perScreen === 2 ? Math.floor((W - 4 * padX) / 2) : Math.floor(W - 2 * padX);
    colW = col;
    setVar('--kl-pad-x', padX + 'px');
    setVar('--kl-pad-y', padY + 'px');
    setVar('--kl-pad-top', padTop + 'px');
    setVar('--kl-pad-bottom', padBottom + 'px');
    setVar('--kl-col', col + 'px');
    setVar('--kl-gap', 2 * padX + 'px');
    setVar('--kl-page-h', Math.max(80, H - padTop - padBottom - 2) + 'px');
  }

  /**
   * Pictures the reader has turned a quarter (one printed on its side to fit a page): the app hands the picture
   * over turned, and where the book gives it a shape of its own (an svg's viewBox, an img's width and height),
   * that's turned too. [turns]: the picture's path in the book to the quarter turns.
   */
  function applyTurns(turns) {
    if (!turns) return;
    var odd = function (el, href) {
      if (!href) return false;
      var path;
      try { path = decodeURIComponent(new URL(href, document.baseURI).pathname.replace(/^\/book\//, '')); } catch (e) { return false; }
      return (turns[path] || 0) % 2 === 1;
    };
    var swap = function (el, a, b) {
      var x = el.getAttribute(a), y = el.getAttribute(b);
      if (x && y && !/%$/.test(x) && !/%$/.test(y)) { el.setAttribute(a, y); el.setAttribute(b, x); }
    };
    var imgs = document.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) if (odd(imgs[i], imgs[i].getAttribute('src'))) swap(imgs[i], 'width', 'height');
    var images = document.querySelectorAll('svg image');
    for (var k = 0; k < images.length; k++) {
      var im = images[k];
      if (!odd(im, im.getAttribute('href') || im.getAttributeNS('http://www.w3.org/1999/xlink', 'href'))) continue;
      swap(im, 'width', 'height');
      var svg = im.closest('svg');
      var vb = svg && svg.getAttribute('viewBox');
      var n = vb ? vb.trim().split(/[\s,]+/) : null;
      if (n && n.length === 4) svg.setAttribute('viewBox', [n[0], n[1], n[3], n[2]].join(' '));
      if (svg) swap(svg, 'width', 'height');
    }
  }

  /** The one picture in [scope], with next to no words by it (a plate, its caption at most); null if it isn't that. */
  function lonePicture(scope) {
    if (!scope || (scope.textContent || '').replace(/\s+/g, '').length >= 80) return null;
    var pics = Array.prototype.filter.call(scope.querySelectorAll('img, svg'), function (el) {
      return !(el.localName === 'svg' && el.parentNode && el.parentNode.closest && el.parentNode.closest('svg'));
    });
    return pics.length === 1 ? pics[0] : null;
  }

  /** Pictures that are a page on their own (covers, plates) fill the page. */
  /**
   * Pictures that are a page on their own (covers, plates) fill the page. Only on a page that's
   * nothing but the picture: a chapter's ornament, even one drawn 100% high, stays in the text.
   */
  function markFullPagePictures() {
    var body = document.body;
    picturePair = null;
    // Two pages side by side that are each just a picture (plates one after another): each fills its page.
    if (body && pairBox()) {
      var a = lonePicture(pairBox()), b = lonePicture(body);
      if (a && b) {
        a.classList.add('kl-full-page');
        b.classList.add('kl-full-page');
        picturePair = [a, b];
        picturePage = true;
      }
      return;
    }
    // Two short pages side by side, or a book's title page with words on it: a picture there is a
    // publisher's mark. (A title page that's only a picture, the title set as an image, is the page.)
    if (!body) return;
    var text = (body.textContent || '').replace(/\s+/g, '');
    if (place === 'title' && text.length > 0) return;
    if (text.length >= 80) return;
    var pics = body.querySelectorAll('img, svg');
    var svgs = body.querySelectorAll('svg');
    for (var i = 0; i < svgs.length; i++) {
      var h = svgs[i].getAttribute('height');
      if (h && /%$/.test(h)) svgs[i].classList.add('kl-full-page');
    }
    if (pics.length === 1) pics[0].classList.add('kl-full-page');
    picturePage = !!document.querySelector('.kl-full-page');
  }

  /**
   * A picture held in an svg keeps its own shape, in the middle of the space it's given, as big as
   * fits. Some books (calibre's covers among them) have it stretched to fill the svg, which only
   * looks right when the svg is the picture's shape; here a cover's svg is the shape of the page, and
   * a single page on a screen held sideways is wide.
   */
  function keepPictureShapes() {
    var stretched = function (el) { return /^\s*none\b/.test(el.getAttribute('preserveAspectRatio') || ''); };
    var svgs = document.querySelectorAll('svg');
    for (var i = 0; i < svgs.length; i++) {
      var svg = svgs[i];
      var images = svg.querySelectorAll('image');
      if (!images.length) continue;
      if (stretched(svg)) svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
      for (var k = 0; k < images.length; k++) {
        if (stretched(images[k])) images[k].setAttribute('preserveAspectRatio', 'xMidYMid meet');
      }
    }
  }

  /**
   * A picture on a line of its own sits in the middle of it, as a printed book has it: the paragraph
   * holding nothing else is centred, without its first-line indent (which would push it off centre).
   */
  function centrePictures() {
    // In the body, and in a chapter's opening or a page beside the next, which the app puts in front of it.
    var roots = [document.body, document.getElementById('kl-opening'), pairBox()];
    for (var r = 0; r < roots.length; r++) {
      var scope = roots[r];
      if (!scope) continue;
      var pics = scope.querySelectorAll('img, svg');
      for (var i = 0; i < pics.length; i++) {
        var pic = pics[i];
        if (pic.localName === 'svg' && pic.parentNode && pic.parentNode.closest && pic.parentNode.closest('svg')) continue;
        var box = pic.parentNode;
        // Out through anything wrapped round just it (a link, a span).
        while (box && box !== scope && box.nodeType === 1 && /^(a|span)$/i.test(box.localName) && holdsOnly(box, pic)) box = box.parentNode;
        if (box && box !== scope && box.nodeType === 1 && holdsOnly(box, pic)) box.classList.add('kl-picture-box');
      }
    }
  }

  /**
   * What's wrapped round just a link (a span, a paragraph holding only it): with links in the text's
   * colour, a colour or underline the book gives the wrapper goes too, as the link's own does.
   */
  function markLinkWrappers() {
    var links = document.querySelectorAll('a[href]');
    for (var i = 0; i < links.length && i < 3000; i++) {
      var a = links[i];
      var said = (a.textContent || '').trim();
      for (var box = a.parentNode; box && box.nodeType === 1 && box !== document.body && box !== document.documentElement; box = box.parentNode) {
        if ((box.textContent || '').trim() !== said || box.querySelectorAll('a[href]').length !== 1) break;
        box.classList.add('kl-link-wrap');
      }
    }
  }

  function holdsOnly(box, pic) {
    if (box.querySelectorAll('img, svg').length !== (pic.localName === 'svg' ? 1 + pic.querySelectorAll('svg').length : 1)) return false;
    var text = (box.textContent || '').replace(pic.textContent || '', '');
    return !text.trim();
  }

  /**
   * A picture keeps its shape when the page is narrower than the width the book gives it: the book's
   * height goes too, rather than leaving it small in a tall box. (And if the page's height then
   * stops it, its width goes as well, so it's as big as fits.)
   */
  function fitPictureBoxes() {
    var misshapen = function (im) {
      if (!im.naturalWidth || !im.naturalHeight || im.classList.contains('kl-full-page')) return false;
      var r = im.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      return Math.abs((r.width / r.height) / (im.naturalWidth / im.naturalHeight) - 1) > 0.03;
    };
    var imgs = document.images, off = [];
    for (var i = 0; i < imgs.length; i++) if (misshapen(imgs[i])) off.push(imgs[i]);
    for (var j = 0; j < off.length; j++) off[j].style.setProperty('height', 'auto', 'important');
    var still = off.filter(misshapen);
    for (var k = 0; k < still.length; k++) still[k].style.setProperty('width', 'auto', 'important');
  }

  function forcedBreak(v) { return /^(page|always|left|right|recto|verso|column)$/.test(v || ''); }

  // ---- a chapter's opening, as a printed book sets it ------------------------------------------------

  /** The first paragraph of real text: one with 100 letters or more of its own. Null if there's none. */
  function firstTextParagraph() {
    var body = document.body;
    if (!body) return null;
    var all = body.getElementsByTagName('*');
    for (var i = 0; i < all.length && i < 400; i++) {
      var e = all[i];
      if (!/^(p|div|blockquote|li)$/i.test(e.localName)) continue;
      var own = '';
      for (var n = e.firstChild; n; n = n.nextSibling) {
        if (n.nodeType === 3) own += n.data;
        else if (n.nodeType === 1 && !/^(p|div|blockquote|ul|ol|table|h[1-6]|figure|section)$/i.test(n.localName)) own += n.textContent || '';
      }
      if (own.replace(/\s+/g, ' ').trim().length >= 100) return e;
    }
    return null;
  }

  var numberWords = '(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|' +
    'eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred)(?:[\\s-](?:one|two|three|four|five|six|seven|eight|nine))?';
  /** A chapter's number on its own: "PROLOGUE", "Chapter 12", "Chapter Twelve", "XII", "12". */
  var chapterNumber = new RegExp('^(?:(?:chapter|chap\\.|prologue|epilogue|interlude|prelude|preface|foreword|introduction|afterword|' +
    'appendix|part|book|section|canto|act|chapitre|kapitel|cap[ií]tulo|capitolo|hoofdstuk|глава|часть|пролог|эпилог)' +
    '(?:\\s+(?:\\d{1,4}|[ivxlcdm]{1,8}|' + numberWords + '))?|\\d{1,4}|[ivxlcdm]{1,8}|' + numberWords + ')[.:]?$', 'i');

  function saidIn(el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); }

  /**
   * A chapter's number and its name in one line ("CHAPTER ONE DARK MATTERS", "Chapter 3: The Road"):
   * they can't be set apart, so the book's own look for them stays.
   */
  function numberAndName(t) {
    var words = t.split(' ');
    for (var k = 1; k < words.length && k <= 4; k++) {
      if (chapterNumber.test(words.slice(0, k).join(' ').replace(/[.:,;\u2013\u2014-]+$/, ''))) return true;
    }
    return false;
  }

  /** The book's own style sheets (not the reader's, nor the browser's) say [prop] for [el], or its own style attribute does. */
  function bookSets(el, prop) {
    if (el.style && el.style.getPropertyValue(prop)) return true;
    var sheets = document.styleSheets;
    for (var i = 0; i < sheets.length; i++) {
      var sh = sheets[i];
      if (sh.href && sh.href.indexOf('/app/reader/') >= 0) continue;
      var rules;
      try { rules = sh.cssRules; } catch (e) { continue; }
      if (rules && rulesSet(rules, el, prop)) return true;
    }
    return false;
  }
  function rulesSet(rules, el, prop) {
    for (var r = 0; r < rules.length; r++) {
      var rule = rules[r];
      if (rule.cssRules && !rule.selectorText) {
        if (rulesSet(rule.cssRules, el, prop)) return true;
        continue;
      }
      if (!rule.selectorText || !rule.style || !rule.style.getPropertyValue(prop)) continue;
      try { if (el.matches(rule.selectorText)) return true; } catch (e) {}
    }
    return false;
  }

  /** The book gives [p] a big first letter of its own: a ::first-letter rule, or a letter (or picture) set apart at its start. */
  function ownInitial(p) {
    var sheets = document.styleSheets;
    for (var i = 0; i < sheets.length; i++) {
      var sh = sheets[i];
      if (sh.href && sh.href.indexOf('/app/reader/') >= 0) continue;
      var rules;
      try { rules = sh.cssRules; } catch (e) { continue; }
      if (rules && firstLetterRule(rules, p)) return true;
    }
    return !!initialIn(p, parseFloat(getComputedStyle(p).fontSize) || 16, 0);
  }

  /**
   * A letter or two (with a quotation mark, say) set apart at the start of [el]: a picture, or a letter floated, made
   * bigger or marked as an initial; looked for inside what [el] opens with too (the first words in small capitals, say).
   * Gives the picture or the letter's element, or null.
   */
  function initialIn(el, pFont, depth) {
    for (var n = el.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3) { if (n.data.trim()) return null; continue; }
      if (n.nodeType !== 1) continue;
      if (/^(img|svg)$/i.test(n.localName)) return n;
      if ((n.textContent || '').trim().length <= 4) {
        var cs = getComputedStyle(n);
        if (/(^|[\s_-])(drop|drop-?cap|initial|init|cap|dc|first-?letter|big)($|[\s_\d-])/i.test(n.getAttribute('class') || '')) return n;
        if (cs.cssFloat && cs.cssFloat !== 'none') return n;
        if ((parseFloat(cs.fontSize) || 0) > pFont * 1.3) return n;
      }
      return depth < 3 ? initialIn(n, pFont, depth + 1) : null;
    }
    return null;
  }

  /** Opening with a quotation mark or a dash, as speech (or a quotation) does. */
  function spoken(t) { return /^[\u201c\u201e\u00ab\u2039\u2018\u201a"'\u300c\u300e\u2014\u2013]/.test(t); }

  /** A line that reads as the story begun rather than a heading: speech, or a sentence. */
  function storyLine(t) { return spoken(t) || /[.!?\u2026]['"\u201d\u2019\u00bb\u203a\u300d\u300f)]*$/.test(t); }

  /** The name of a page a book has before or after its story: its copyright, a map, its contents, a list of its characters. */
  var notAChapter = /\b(copyright|maps?|contents|dedication|acknowledg\w*|about the|also by|books by|other books|newsletter|mailing list|glossary|dramatis|cast of|characters|pronunciation|timeline|credits|colophon|imprint|title page|bibliography|index|praise|preview|excerpt|sneak peek|bonus)\b/i;

  /**
   * The chapter is a chapter of the book's text, to open with a drop cap: it has a chapter's number ("Chapter 3",
   * "Prologue"), or under a name, a good deal of prose (not a copyright page, a map, or a title page with a line or two).
   */
  function realChapter(number, name, together) {
    if (number || together) return true;
    if (!name) return false;
    var prose = (document.body.textContent || '').replace(/\s+/g, ' ').length;
    return prose >= 6000 || (prose >= 1500 && !notAChapter.test(saidIn(name)));
  }

  /** Set like the text's paragraphs: not a heading, an epigraph, or a place and date set apart. */
  function setLikeText(el, bodySize) {
    if (headingLike(el, bodySize)) return false;
    var cs = getComputedStyle(el);
    if ((parseFloat(cs.fontSize) || 0) < bodySize * 0.95) return false;
    if (cs.textAlign === 'right' || cs.textAlign === 'end') return false;
    for (var a = el.parentElement; a && a !== document.body; a = a.parentElement) {
      if (/^(blockquote|aside|figure|header)$/i.test(a.localName)) return false;
    }
    return true;
  }
  function firstLetterRule(rules, p) {
    for (var r = 0; r < rules.length; r++) {
      var rule = rules[r];
      if (rule.cssRules && !rule.selectorText) {
        if (firstLetterRule(rule.cssRules, p)) return true;
        continue;
      }
      var sel = rule.selectorText || '';
      if (!/:?:first-letter/i.test(sel)) continue;
      var parts = sel.split(',');
      for (var k = 0; k < parts.length; k++) {
        if (!/first-letter/i.test(parts[k])) continue;
        var plain = parts[k].replace(/:?:first-letter/ig, '').trim() || '*';
        try { if (p.matches(plain)) return true; } catch (e) {}
      }
    }
    return false;
  }

  /** The book sets the look of [el] (or of what's in it) itself: its size, style or spacing. */
  function bookStyles(el) {
    var props = ['font-size', 'font-style', 'letter-spacing', 'text-transform', 'font-variant'];
    var els = [el].concat(Array.prototype.slice.call(el.getElementsByTagName('*'), 0, 12));
    for (var i = 0; i < els.length; i++) {
      for (var p = 0; p < props.length; p++) if (bookSets(els[i], props[p])) return true;
    }
    return false;
  }

  /** A heading, or a short line set apart like one (bigger, centred, italic or bold). */
  function headingLike(el, bodySize) {
    if (/^h[1-6]$/i.test(el.localName)) return true;
    var cs = getComputedStyle(el);
    return (parseFloat(cs.fontSize) || 0) > bodySize * 1.1 || cs.textAlign === 'center' || cs.fontStyle === 'italic' || (parseInt(cs.fontWeight, 10) || 400) >= 600;
  }

  /**
   * At the start of a chapter: its number ("PROLOGUE", "Chapter 12") and its name, marked to be set as a
   * printed book has them (when the reader asks for that), and its first paragraph marked for a drop cap,
   * unless the book gives it one of its own. Only the lines before the first paragraph of text are looked at.
   */
  function markOpening() {
    var old = document.querySelectorAll('.kl-chapnum, .kl-chapname, .kl-dropcap, .kl-initial, .kl-initial-letter');
    for (var o = 0; o < old.length; o++) old[o].classList.remove('kl-chapnum', 'kl-chapname', 'kl-dropcap', 'kl-unstyled', 'kl-initial', 'kl-initial-letter');
    if (!chapterOpening) return;
    var first = firstTextParagraph();
    if (!first) return;
    var bodySize = parseFloat(getComputedStyle(first).fontSize) || 16;
    var lines = [];
    var scopes = [document.getElementById('kl-opening'), document.body];
    for (var s2 = 0; s2 < scopes.length && lines.length < 4; s2++) {
      var scope = scopes[s2];
      if (!scope) continue;
      var all = scope.getElementsByTagName('*');
      for (var i = 0; i < all.length && lines.length < 4; i++) {
        var el = all[i];
        if (el === first || (el.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_PRECEDING)) break;
        if (!/^(h[1-6]|p|div)$/i.test(el.localName)) continue;
        var t = saidIn(el);
        if (!t || t.length > 90) continue;
        // The line itself, not a box round several.
        if (el.querySelector('p, div, h1, h2, h3, h4, h5, h6, blockquote, li, table')) continue;
        lines.push(el);
      }
    }
    var number = null, name = null, together = false, opening = null, k = 0;
    for (; k < lines.length; k++) {
      var line = lines[k], said = saidIn(line), heading = headingLike(line, bodySize);
      if (!number && !name && chapterNumber.test(said)) { number = line; continue; }
      // The text has begun, however short its first paragraph: a big first letter, or speech or a sentence set like the text.
      var real = /^h[1-6]$/i.test(line.localName);
      if ((!real && ownInitial(line)) || (!heading && storyLine(said))) { opening = line; break; }
      // A quotation set apart (an epigraph, say) is no chapter's name: look on past it.
      if (!real && spoken(said)) continue;
      if (!number && !name && heading && numberAndName(said)) { together = true; k++; break; }
      if (!name && (number || heading)) { name = line; k++; break; }
    }
    // The text's first paragraph: the first line after the chapter's number and name set like the text (passing over an
    // epigraph, say), or else the first long paragraph.
    // (A short line only if it's speech or a sentence: not a line of a title page, "A Novel of the North".)
    for (; !opening && k < lines.length; k++) {
      if (ownInitial(lines[k]) || (setLikeText(lines[k], bodySize) && storyLine(saidIn(lines[k])))) opening = lines[k];
    }
    var start = opening || first;
    // Set as in print whatever the book says when the reader asks for that; otherwise only where the book
    // doesn't set their look itself (kl-unstyled).
    if (number) { number.classList.add('kl-chapnum'); if (!bookStyles(number)) number.classList.add('kl-unstyled'); }
    if (name) { name.classList.add('kl-chapname'); if (!bookStyles(name)) name.classList.add('kl-unstyled'); }
    // A drop cap where a chapter starts, as in print, unless the book has its own: then that paragraph isn't indented,
    // which would set the big letter in from the margin and the words after it further still. (With large first
    // letters turned off, the book's own letter is set like the rest of the text; a picture is left as it is.)
    if (ownInitial(start)) {
      start.classList.add('kl-initial');
      var letter = initialIn(start, parseFloat(getComputedStyle(start).fontSize) || 16, 0);
      if (letter && !/^(img|svg)$/i.test(letter.localName)) letter.classList.add('kl-initial-letter');
    } else if (realChapter(number, name, together)) start.classList.add('kl-dropcap');
  }

  /**
   * A chapter's opening (its number, an ornament, its title) stays with the first lines of the text:
   * page breaks the book asks for between them are dropped, and headings and pictures there keep
   * with what follows. The opening is everything before the first paragraph of real text.
   */
  function keepOpeningTogether() {
    var body = document.body;
    if (!body) return;
    var all = body.getElementsByTagName('*');
    var first = null;
    for (var i = 0; i < all.length && i < 400; i++) {
      var e = all[i];
      if (!/^(p|div|blockquote|li)$/i.test(e.localName)) continue;
      var own = '';
      for (var n = e.firstChild; n; n = n.nextSibling) {
        if (n.nodeType === 3) own += n.data;
        else if (n.nodeType === 1 && !/^(p|div|blockquote|ul|ol|table|h[1-6]|figure|section)$/i.test(n.localName)) own += n.textContent || '';
      }
      if (own.replace(/\s+/g, ' ').trim().length >= 100) { first = e; break; }
    }
    if (!first) return;
    // A chapter's opening from a file of its own, put in front of the chapter by the app: nothing in
    // it or after it may start a new page, so it shares the first page with the chapter's start.
    var opening = document.getElementById('kl-opening');
    if (opening) {
      var own = [opening].concat(Array.prototype.slice.call(opening.getElementsByTagName('*')));
      for (var k = 0; k < own.length; k++) {
        var o = own[k].style;
        o.setProperty('break-before', 'auto', 'important');
        o.setProperty('page-break-before', 'auto', 'important');
        o.setProperty('break-after', 'avoid', 'important');
        o.setProperty('page-break-after', 'avoid', 'important');
      }
      body.style.setProperty('break-before', 'auto', 'important');
      body.style.setProperty('page-break-before', 'auto', 'important');
    }
    var seenContent = !!opening;
    for (var j = 0; j < all.length; j++) {
      var el = all[j];
      if (el === first) break;
      if (el.contains(first)) continue;
      var cs = getComputedStyle(el);
      var hasContent = (el.textContent || '').trim().length > 0 || /^(img|svg|image)$/i.test(el.localName) || el.querySelector('img, svg');
      // Nothing before it yet: a break there makes no difference, and the chapter starts on its own page anyway.
      if (seenContent && (forcedBreak(cs.breakBefore) || forcedBreak(cs.pageBreakBefore))) {
        el.style.setProperty('break-before', 'auto', 'important');
        el.style.setProperty('page-break-before', 'auto', 'important');
      }
      if (forcedBreak(cs.breakAfter) || forcedBreak(cs.pageBreakAfter) || /^h[1-6]$/i.test(el.localName) || /^(img|svg|figure)$/i.test(el.localName)) {
        el.style.setProperty('break-after', 'avoid', 'important');
        el.style.setProperty('page-break-after', 'avoid', 'important');
      }
      if (hasContent) seenContent = true;
    }
    var fs = getComputedStyle(first);
    if (seenContent && (forcedBreak(fs.breakBefore) || forcedBreak(fs.pageBreakBefore))) {
      first.style.setProperty('break-before', 'auto', 'important');
      first.style.setProperty('page-break-before', 'auto', 'important');
    }
  }

  // ---- layout -----------------------------------------------------------------------------------

  // When scrolling: how much of the screen shows text between the bands, how far a "page" goes (a
  // screen, less a line so the last line read is still there), and how far down the chapter can go.
  function viewH() { return Math.max(40, H - bandTop - bandBottom); }
  function stepH() { return Math.max(40, viewH() - Math.round(lineH * 1.5)); }
  function maxScroll() { return Math.max(0, scroller.scrollHeight - H); }
  /** How far a scrolled chapter goes until its last line sits at the foot of the screen (the space under it aside). */
  function textMax() {
    var bottom = document.body.getBoundingClientRect().bottom + scroller.scrollTop;
    return Math.max(0, Math.min(maxScroll(), bottom + padY - (H - bandBottom)));
  }
  /** Where a place gone to sits on the screen: just under the top band. */
  function topInset() { return bandTop + Math.round(padY * 0.6); }

  function countPages() {
    laidOutWidth = W;
    var fill = pageFill();
    if (scrolled) {
      if (fill) fill.style.display = 'none';
      pages = Math.max(1, Math.ceil(textMax() / stepH()) + 1);
      columns = pages;
      displayPage = false;
      page = scrolledPage();
      return pages;
    }
    // Counted without the filler, which then goes at the far edge of the last screen; and laid out as the
    // book has it, before a page alone is moved to the right or a wide picture across both pages.
    if (fill) fill.style.display = 'none';
    root.classList.remove('kl-recto', 'kl-spread-pic', 'kl-centre', 'kl-spread-pair', 'kl-recto-open', 'kl-placed');
    unplaceHalves();
    unplacePictures();
    blankBefore = 0;
    spreadPicture = false;
    var w = scroller.scrollWidth;
    pages = W > 0 ? Math.max(1, Math.ceil((w - 2) / W)) : 1;
    // Each column is a page. The last one is the column the chapter's last words (or picture) are in: the
    // width scrolled can't say, as it's never less than the screen's, however little the chapter fills.
    columns = perScreen === 2 && W > 0 ? Math.max(1, Math.min(pages * 2, lastColumn() + 1)) : pages;
    // Nothing after them but empty space (a page break the book asks for at its end): no screens for it.
    if (perScreen === 2) pages = Math.max(1, Math.min(pages, Math.ceil(columns / 2)));
    // Two short pages side by side are both pages a book doesn't number; one alone, if it's one of those.
    var pair = !!pairBox();
    // A map printed across two pages and kept as its two halves: the two pages of one screen, joined.
    var halves = perScreen === 2 && !pair && pages <= 2 ? spreadHalves() : null;
    if (halves) {
      pages = 1;
      columns = 2;
    }
    displayPage = pair || !!halves || (columns === 1 && pages === 1 && looksLikeDisplayPage());
    var gutter = 8;
    if (picturePair && perScreen === 2) {
      // Two plates one after another: side by side, each on its page, as big as fits, into the margins.
      pages = 1;
      columns = 2;
      if (!countingOnly) {
        placePicture(picturePair[0], 12, W / 2 - gutter);
        placePicture(picturePair[1], W / 2 + gutter, W - 12);
        root.classList.add('kl-placed');
      }
    } else if (picturePair && !countingOnly) {
      // One page at a time: each on its own page, as the flow has them.
    } else if (perScreen === 1 && pages === 1 && picturePage && !pair && !countingOnly) {
      // A plate alone, one page at a time: the whole screen, into the margins.
      var lone = document.querySelector('.kl-full-page');
      if (lone) {
        placePicture(lone, 12, W - 12);
        root.classList.add('kl-placed');
      }
    }
    if (perScreen === 2 && pages === 1 && !pair) {
      var wide = picturePage && widePicture();
      if (halves) {
        spreadPicture = true;
        setVar('--kl-spread-top', Math.max(12, Math.round((settings.minTop || 0) - 10)) + 'px');
        setVar('--kl-spread-bottom', Math.max(12, Math.round((settings.minBottom || 0) - 10)) + 'px');
        // Only shown, not counted: it's two pages either way.
        if (!countingOnly) placeHalves(halves);
        root.classList.add('kl-spread-pair');
      } else if (picturePage && (wide || centred())) {
        // A map or a picture made to go across two pages: across the screen, in the middle. A cover: in
        // the middle, as big as fits.
        spreadPicture = true;
        columns = wide ? 2 : 1;
        displayPage = true;
        // Into the margins, up to the rows of the time and the rest over and under the page, if they're there.
        setVar('--kl-spread-top', Math.max(12, Math.round((settings.minTop || 0) - 10)) + 'px');
        setVar('--kl-spread-bottom', Math.max(12, Math.round((settings.minBottom || 0) - 10)) + 'px');
        root.classList.add('kl-spread-pic');
      } else if (columns === 1 && centred()) {
        // A book's title page: in the middle of the screen.
        setVar('--kl-centre-shift', Math.round(W / 2 - padX - colW / 2) + 'px');
        root.classList.add('kl-centre');
      } else if (columns === 1 && place !== 'left') {
        // A page on its own (an epigraph, a notice, a plate) goes on the right, as in print. (A
        // dedication stays on the left.) A plate fills its page, into the margins. (With pieces of the page put in
        // front of it by the app, a part's title and an ornament, the blank page goes before them too.)
        blankBefore = 1;
        root.classList.add(document.getElementById('kl-opening') ? 'kl-recto-open' : 'kl-recto');
        var plate = picturePage && document.querySelector('.kl-full-page');
        if (plate && !countingOnly) {
          placePicture(plate, W / 2 + gutter, W - 12);
          root.classList.add('kl-placed');
        }
      }
    }
    // A chapter starts on a right-hand page, as in print, the left-hand one beside it blank. (Its pages of
    // text are counted as they were; only the screens they're on move along by a page.)
    if (perScreen === 2 && settings.rectoOpenings && chapterOpening && !countingOnly && blankBefore === 0 &&
        !pair && !halves && !spreadPicture && !centred() && !displayPage && place !== 'left') {
      root.classList.add('kl-recto-open');
      blankBefore = 1;
      pages = Math.max(1, Math.ceil((columns + 1) / 2));
    }
    if (fill) {
      fill.style.left = (pages * W - 1) + 'px';
      fill.style.display = 'block';
    }
    return pages;
  }

  /**
   * The chapter, on one page, is one a printed book wouldn't number: a picture, little text (a dedication, a
   * quote, a part's title), or marked as a dedication or epigraph by the book.
   */
  function looksLikeDisplayPage() {
    var body = document.body;
    if (!body) return false;
    if (picturePage) return true;
    if ((body.textContent || '').replace(/\s+/g, '').length < 700) return true;
    var marks = /(^|[^a-z])(dedication|epigraphs?|titlepage|halftitle(page)?|half-title|frontispiece|pullquote)([^a-z]|$)/i;
    if (marks.test(decodeURIComponent((pagePath || location.pathname || '').split('/').pop() || ''))) return true;
    var els = [body].concat(Array.prototype.slice.call(body.querySelectorAll('section, div, blockquote, aside')).slice(0, 6));
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      var said = [e.getAttribute('epub:type'), e.getAttributeNS && e.getAttributeNS('http://www.idpf.org/2007/ops', 'type'),
        e.getAttribute('role'), e.getAttribute('class'), e.id].join(' ');
      if (marks.test(said)) return true;
    }
    return false;
  }

  /** The column (counted from 0) the chapter's last words or picture are in, laid out as pages. */
  function lastColumn() {
    var body = document.body;
    if (!body || W <= 0) return 0;
    var pitch = W / perScreen, right = 0;
    var x0 = scroller.scrollLeft;
    // The last words: the last text with more than spaces in it that's laid out (not hidden).
    var tw = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null), n, texts = [];
    while ((n = tw.nextNode())) if (n.data.trim()) texts.push(n);
    var r = document.createRange();
    for (var t = texts.length - 1; t >= 0 && t >= texts.length - 40; t--) {
      r.selectNodeContents(texts[t]);
      var rects = r.getClientRects();
      if (rects.length) { right = Math.max(right, rects[rects.length - 1].left + x0); break; }
    }
    // The last picture, if it comes after them.
    var pics = body.querySelectorAll('img, svg, video');
    for (var i = pics.length - 1; i >= 0; i--) {
      var b = pics[i].getBoundingClientRect();
      if (b.width > 1 && b.height > 1) { right = Math.max(right, b.left + x0); break; }
    }
    return Math.max(0, Math.floor((right + 1) / pitch));
  }

  /**
   * A picture marked as the whole page that turns out small, with words by it (a publisher's mark on a
   * title page): left as it is, not made to fill the page. Once the pictures have loaded.
   */
  function unmarkSmallPictures() {
    if (!document.body) return;
    // A picture with no words by it is the page however small it is, unless it's an ornament (a divider, a
    // flourish: small, or long and low), which isn't blown up to fill the page or spread across two.
    var words = !!(document.body.textContent || '').trim();
    var marked = document.querySelectorAll('.kl-full-page');
    for (var i = 0; i < marked.length; i++) {
      var el = marked[i], w = 0, h = 0;
      if (el.localName === 'img') { w = el.naturalWidth; h = el.naturalHeight; }
      else {
        var im = el.querySelector && el.querySelector('image');
        if (im) { w = parseFloat(im.getAttribute('width')) || 0; h = parseFloat(im.getAttribute('height')) || 0; }
      }
      var small = w > 0 && h > 0 && w < 400 && h < 400;
      var ornament = w > 0 && h > 0 && ((w < 700 && h < 400) || (w >= 3 * h && h < 500));
      // (The book's cover, as the app says, is the cover however small.)
      if (words ? small : (ornament && place !== 'cover')) el.classList.remove('kl-full-page');
    }
    picturePage = !!document.querySelector('.kl-full-page');
    if (picturePair && !(picturePair[0].classList.contains('kl-full-page') && picturePair[1].classList.contains('kl-full-page'))) {
      picturePair[0].classList.remove('kl-full-page');
      picturePair[1].classList.remove('kl-full-page');
      picturePair = null;
      picturePage = false;
    }
  }

  /**
   * A map (or another picture) printed across two facing pages, kept in the book as two pictures: its
   * left half and its right. The chapter is nothing but the two (a few words at most, such as a title),
   * each taller than it's wide, as half of something wide is, and the same height, give or take a little
   * (the halves are often scanned apart). [{el, w, h}] for each, left first; null if it isn't that.
   */
  function spreadHalves() {
    var body = document.body;
    if (!body) return null;
    var pics = [];
    var all = body.querySelectorAll('img, svg');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.localName === 'svg' && el.parentNode && el.parentNode.closest && el.parentNode.closest('svg')) continue;
      pics.push(el);
    }
    if (pics.length !== 2) return null;
    var words = body.textContent || '';
    for (var k = 0; k < pics.length; k++) words = words.replace(pics[k].textContent || '', '');
    if (words.replace(/\s+/g, '').length >= 80) return null;
    var out = [];
    for (var j = 0; j < 2; j++) {
      var size = naturalSize(pics[j]);
      if (!size) return null;
      var shape = size.w / size.h;
      if (shape < 0.3 || shape > 1.05) return null;
      out.push({ el: pics[j], w: size.w, h: size.h });
    }
    if (Math.abs(out[0].h - out[1].h) / Math.max(out[0].h, out[1].h) > 0.1) return null;
    return out;
  }

  /** A picture's own size: an img's pixels, or an svg's (from its viewBox, or the picture in it). */
  function naturalSize(el) {
    var w = 0, h = 0;
    if (el.localName === 'img') { w = el.naturalWidth; h = el.naturalHeight; }
    else {
      var vb = el.viewBox && el.viewBox.baseVal;
      if (vb && vb.width && vb.height) { w = vb.width; h = vb.height; }
      var im = el.querySelector && el.querySelector('image');
      if ((!w || !h) && im) { w = parseFloat(im.getAttribute('width')) || 0; h = parseFloat(im.getAttribute('height')) || 0; }
    }
    return w > 0 && h > 0 ? { w: w, h: h } : null;
  }

  // The halves placed across the screen, and how much of each was left off at the join (see placeHalves).
  var placedHalves = [];

  /**
   * The two halves across the whole screen, into the margins, in the middle: the same height, side by
   * side, joined where they were cut. A blank strip at either inside edge (the gap a scan leaves there)
   * is left off, so the map runs on across the join.
   */
  function placeHalves(halves) {
    var top = Math.max(12, Math.round((settings.minTop || 0) - 10));
    var bottom = Math.max(12, Math.round((settings.minBottom || 0) - 10));
    var availW = W - 24, availH = Math.max(40, H - top - bottom);
    var a = halves[0], b = halves[1];
    a.cut = a.el.localName === 'img' ? blankEdge(a.el, true) : 0;
    b.cut = b.el.localName === 'img' ? blankEdge(b.el, false) : 0;
    var ka = a.w * (1 - a.cut) / a.h, kb = b.w * (1 - b.cut) / b.h;
    var h = Math.min(availH, availW / (ka + kb));
    var wa = Math.round(h * ka), wb = Math.round(h * kb);
    h = Math.round(h);
    var x = Math.round((W - wa - wb) / 2), y = Math.round(top + (availH - h) / 2);
    putHalf(a.el, x, y, wa, h, '0% 50%');
    putHalf(b.el, x + wa, y, wb, h, '100% 50%');
    placedHalves = [a, b];
  }

  function putHalf(el, x, y, w, h, at) {
    el.classList.add('kl-spread-half');
    var st = el.style;
    st.setProperty('left', x + 'px', 'important');
    st.setProperty('top', y + 'px', 'important');
    st.setProperty('width', w + 'px', 'important');
    st.setProperty('height', h + 'px', 'important');
    st.setProperty('object-position', at, 'important');
  }

  /** The room a picture placed on the screen has, into the margins: up to the rows over and under the page. */
  function pictureRoom() {
    return {
      top: Math.max(12, Math.round((settings.minTop || 0) - 10)),
      bottom: Math.max(12, Math.round((settings.minBottom || 0) - 10)),
    };
  }

  /** [el] set in the box from [x] to [x2] across, as big as fits and in the middle of it, into the margins. */
  function placePicture(el, x, x2) {
    var room = pictureRoom();
    el.classList.add('kl-placed-pic');
    var st = el.style;
    st.setProperty('left', Math.round(x) + 'px', 'important');
    st.setProperty('top', room.top + 'px', 'important');
    st.setProperty('width', Math.max(20, Math.round(x2 - x)) + 'px', 'important');
    st.setProperty('height', Math.max(20, H - room.top - room.bottom) + 'px', 'important');
    placedPics.push(el);
  }

  function unplacePictures() {
    for (var i = 0; i < placedPics.length; i++) {
      var el = placedPics[i];
      el.classList.remove('kl-placed-pic');
      ['left', 'top', 'width', 'height'].forEach(function (p) { el.style.removeProperty(p); });
    }
    placedPics = [];
  }

  function unplaceHalves() {
    for (var i = 0; i < placedHalves.length; i++) {
      var el = placedHalves[i].el;
      el.classList.remove('kl-spread-half');
      ['left', 'top', 'width', 'height', 'object-position'].forEach(function (p) { el.style.removeProperty(p); });
    }
    placedHalves = [];
  }

  /**
   * How much of a picture's width, at its right edge ([right]) or its left, is blank: columns with ink in
   * no more of their height than a frame's lines take. At most a twelfth of the width; 0 if it can't be read.
   */
  function blankEdge(img, right) {
    try {
      var px = pixels(img, 480, false);
      var d = px.d, w = px.w, h = px.h;
      var lum = new Array(w * h);
      var hist = new Array(256).fill(0);
      for (var i = 0, p = 0; i < d.length; i += 4, p++) {
        var l = Math.round(0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2]);
        lum[p] = l;
        hist[l]++;
      }
      // The paper: the shade at which the lightest twentieth starts.
      var seen = 0, paper = 255;
      for (var v = 255; v >= 0; v--) { seen += hist[v]; if (seen >= w * h * 0.05) { paper = v; break; } }
      var most = Math.floor(w / 12), n = 0;
      for (; n < most; n++) {
        var x = right ? w - 1 - n : n, ink = 0;
        for (var y = 0; y < h; y++) if (paper - lum[y * w + x] > 60) ink++;
        if (ink > h * 0.03) break;
      }
      return n / w;
    } catch (e) { log(e); return 0; }
  }

  /** The chapter's one picture is wider than it is tall, as a map across two pages is. */
  function widePicture() {
    var el = document.querySelector('.kl-full-page');
    if (!el) return false;
    var w = 0, h = 0;
    if (el.localName === 'img') { w = el.naturalWidth; h = el.naturalHeight; }
    else {
      var vb = el.viewBox && el.viewBox.baseVal;
      if (vb && vb.width && vb.height) { w = vb.width; h = vb.height; }
      var im = el.querySelector && el.querySelector('image');
      if ((!w || !h) && im) { w = parseFloat(im.getAttribute('width')) || 0; h = parseFloat(im.getAttribute('height')) || 0; }
    }
    return w > 0 && h > 0 && w / h > 1.15;
  }

  /**
   * A speck at the far edge of the last screen, so the chapter can be scrolled to it whole. The
   * columns end short of that edge (by the last column's margin, or by a whole page when the last
   * screen has text on its left-hand page only), and without it the last screen can only be
   * scrolled to part way, showing the edge of the page before. Outside the body, so the chapter is unchanged.
   */
  function pageFill() {
    var el = document.getElementById('kl-pagefill');
    if (el) return el;
    try {
      el = document.createElementNS(XHTML, 'div');
      el.id = 'kl-pagefill';
      el.setAttribute('aria-hidden', 'true');
      root.appendChild(el);
      return el;
    } catch (e) { log(e); return null; }
  }

  /** Which screenful a scrolled chapter is at. */
  function scrolledPage() {
    var max = textMax();
    if (max <= 0) return 0;
    if (scroller.scrollTop >= max - 2) return pages - 1;
    return Math.max(0, Math.min(pages - 1, Math.round(scroller.scrollTop / stepH())));
  }

  /** How far down a scrolled chapter, 0 to 1: 1 once its last line is on screen. */
  function scrolledProgress() {
    var max = textMax();
    return max > 0 ? Math.max(0, Math.min(1, scroller.scrollTop / max)) : 0;
  }

  function setScroll(x) { scroller.scrollLeft = x; if (scroller.scrollLeft !== x) window.scrollTo(x, 0); }
  function setScrollY(y) {
    y = Math.max(0, Math.min(maxScroll(), y));
    scroller.scrollTop = y;
    if (Math.abs(scroller.scrollTop - y) > 1) window.scrollTo(0, y);
  }

  function showPage(n, animate, done) {
    n = Math.max(0, Math.min(pages - 1, n));
    if (scrolled) {
      showY(n >= pages - 1 && n > 0 ? textMax() : n * stepH(), animate, done);
      return;
    }
    var x = n * W;
    var changed = n !== page;
    page = n;
    cancelAnimation();
    if (animate && settings.animate !== false && Math.abs(scroller.scrollLeft - x) <= W * 1.5) {
      animateTo(x, 230, function () { afterTurn(changed); if (done) done(); });
    } else {
      setScroll(x);
      afterTurn(changed);
      if (done) done();
    }
  }

  /** Scrolls a scrolled chapter to [y], gently if [animate]. */
  function showY(y, animate, done) {
    y = Math.max(0, Math.min(maxScroll(), Math.round(y)));
    cancelAnimation();
    var finish = function () {
      var before = page;
      page = scrolledPage();
      afterTurn(page !== before);
      if (done) done();
    };
    if (animate && Math.abs(scroller.scrollTop - y) <= H * 2) animateTo(y, 300, finish, true);
    else { setScrollY(y); finish(); }
  }

  function afterTurn(changed) {
    if (changed) clearHighlight('kl-search');
    if (changed) autoPageTurned();
    report();
  }

  var raf = 0;
  function cancelAnimation() { if (raf) cancelAnimationFrame(raf); raf = 0; animating = 0; }
  function animateTo(x, ms, done, vertical) {
    var from = vertical ? scroller.scrollTop : scroller.scrollLeft, d = x - from, t0 = performance.now();
    animating = 1;
    function step(now) {
      var p = Math.min(1, (now - t0) / ms);
      var e = 1 - Math.pow(1 - p, 3);
      if (vertical) setScrollY(Math.round(from + d * e)); else setScroll(Math.round(from + d * e));
      if (p < 1) raf = requestAnimationFrame(step);
      else { raf = 0; animating = 0; if (done) done(); }
    }
    raf = requestAnimationFrame(step);
  }

  /** Curl and Fade are drawn by the app over the page: the page just jumps, when the app says. */
  function appTurns() { return !scrolled && (settings.turn === 'curl' || settings.turn === 'sweep' || settings.turn === 'fade'); }

  /** [y]: where on the page the turn was asked for (0 top, 1 bottom), for where a curl lifts. */
  function next(y) {
    if (scrolled) {
      // A screen on, or at the end, on to the next chapter.
      if (scroller.scrollTop < maxScroll() - 2) showY(scroller.scrollTop + stepH(), true);
      else KL.edge(1);
      return;
    }
    if (page < pages - 1) {
      if (appTurns()) { KL.turn(1, y == null ? 0.8 : y); return; }
      showPage(page + 1, true);
    } else KL.edge(1);
  }
  function prev(y) {
    if (scrolled) {
      if (scroller.scrollTop > 2) showY(scroller.scrollTop - stepH(), true);
      else KL.edge(-1);
      return;
    }
    if (page > 0) {
      if (appTurns()) { KL.turn(-1, y == null ? 0.8 : y); return; }
      showPage(page - 1, true);
    } else KL.edge(-1);
  }

  /**
   * The app's turn: straight to the next or previous page. False at the first or last page; otherwise
   * where the page is now, so the app shows its headings and number before it copies it for the turn
   * (the report that follows a moment later says the same).
   */
  function turnNow(dir) {
    if (scrolled) return false;
    var n = page + (dir > 0 ? 1 : -1);
    if (n < 0 || n > pages - 1) return false;
    showPage(n, false);
    try { return JSON.stringify(here()); } catch (e) { log(e); return true; }
  }

  // ---- where the reader is ---------------------------------------------------------------------

  function textNodes() {
    var out = [];
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    for (var n = w.nextNode(); n; n = w.nextNode()) out.push(n);
    return out;
  }

  function rectOf(node, offset) {
    var r = document.createRange();
    try {
      if (node.nodeType === 3) {
        var o = Math.min(offset || 0, node.data.length);
        r.setStart(node, o);
        r.setEnd(node, Math.min(node.data.length, o + 1));
      } else r.selectNode(node);
    } catch (e) { return null; }
    var rects = r.getClientRects();
    for (var i = 0; i < rects.length; i++) if (rects[i].width || rects[i].height) return rects[i];
    var b = r.getBoundingClientRect();
    return (b.width || b.height) ? b : null;
  }

  /** Which page a point in the chapter (node and offset) is on. */
  function pageOf(node, offset) {
    var r = rectOf(node, offset);
    if (!r) return null;
    return Math.max(0, Math.min(pages - 1, Math.floor((r.left + scroller.scrollLeft + 1) / W)));
  }

  /** How far down a scrolled chapter a point in it (node and offset) is, or null. */
  function yOf(node, offset) {
    var r = rectOf(node, offset);
    return r ? r.top + scroller.scrollTop : null;
  }

  /** The first character showing under the top band of a scrolled chapter. */
  function firstVisibleScrolled() {
    var top = bandTop + 3;
    var box = document.body.getBoundingClientRect();
    var x = Math.max(4, box.left + 4);
    if (document.caretRangeFromPoint) {
      for (var dy = 0; dy <= 30; dy += 10) {
        var r = document.caretRangeFromPoint(x, top + dy);
        if (r && r.startContainer && r.startContainer.nodeType === 3 && document.body.contains(r.startContainer) && r.startContainer.data.trim()) {
          var rc = rectOf(r.startContainer, r.startOffset);
          if (rc && rc.bottom > bandTop && rc.top < H - bandBottom) return { node: r.startContainer, offset: r.startOffset };
        }
      }
    }
    // Otherwise the first text that reaches below the band.
    var nodes = textNodes();
    var lo = 0, hi = nodes.length - 1, found = -1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      var rs = rangeRects(nodes[mid]);
      var bottom = rs.length ? rs[rs.length - 1].bottom : -1e9;
      if (bottom > bandTop) { found = mid; hi = mid - 1; } else lo = mid + 1;
    }
    for (var i = Math.max(0, found); i >= 0 && i < nodes.length; i++) {
      var n = nodes[i];
      if (!n.data.trim()) continue;
      var q = rangeRects(n);
      if (!q.length) continue;
      if (q[0].top >= H - bandBottom) break;
      // The first character in it below the band.
      var a = 0, b = n.data.length;
      while (a < b) {
        var m = (a + b) >> 1;
        var c = rectOf(n, m);
        if (c && c.bottom > bandTop) b = m; else a = m + 1;
      }
      return { node: n, offset: Math.min(a, n.data.length) };
    }
    return null;
  }

  /** The first character on the current page. */
  function firstVisible() {
    if (scrolled) return firstVisibleScrolled();
    var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(padX + 3, padTop + 6) : null;
    if (r && r.startContainer && r.startContainer.nodeType === 3 && document.body.contains(r.startContainer)) {
      var p = pageOf(r.startContainer, r.startOffset);
      if (p === page) return { node: r.startContainer, offset: r.startOffset };
    }
    // Otherwise the first text node that reaches onto this page.
    var nodes = textNodes();
    var lo = 0, hi = nodes.length - 1, found = -1;
    var left = page * W;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      var rects = rangeRects(nodes[mid]);
      var end = rects.length ? rects[rects.length - 1].right + scroller.scrollLeft : -1;
      if (end > left) { found = mid; hi = mid - 1; } else lo = mid + 1;
    }
    for (var i = Math.max(0, found); i >= 0 && i < nodes.length; i++) {
      var n = nodes[i];
      if (!n.data.trim()) continue;
      var rs = rangeRects(n);
      for (var k = 0; k < rs.length; k++) {
        if (rs[k].right + scroller.scrollLeft > left && rs[k].left + scroller.scrollLeft < left + W) {
          return { node: n, offset: offsetAtPage(n, left) };
        }
      }
      if (rs.length && rs[0].left + scroller.scrollLeft >= left + W) break;
    }
    return null;
  }

  function rangeRects(n) {
    var r = document.createRange();
    r.selectNodeContents(n);
    return r.getClientRects();
  }

  function offsetAtPage(n, left) {
    var lo = 0, hi = n.data.length;
    while (lo < hi) {
      var mid = (lo + hi) >> 1;
      var r = rectOf(n, mid);
      if (r && r.left + scroller.scrollLeft >= left) hi = mid; else lo = mid + 1;
    }
    return lo;
  }

  function pathOf(node) {
    var parts = [];
    while (node && node !== document.body) {
      var i = 0;
      for (var s = node.previousSibling; s; s = s.previousSibling) i++;
      parts.unshift(i);
      node = node.parentNode;
    }
    return node ? parts.join('/') : null;
  }

  function nodeAt(path) {
    var node = document.body;
    var parts = String(path).split('/');
    for (var i = 0; i < parts.length && node; i++) {
      if (parts[i] === '') continue;
      node = node.childNodes[parseInt(parts[i], 10)];
    }
    return node || null;
  }

  /**
   * A saved place: "path:offset" (pathOf's path, and the character), or "chars=N", N letters into the
   * chapter (where the Kobo's place was found; see ChapterLetters in the app).
   */
  function placeOf(value) {
    var s = String(value || '');
    if (s.indexOf('chars=') === 0) return pointAtLetters(parseInt(s.slice(6), 10) || 0);
    var bits = s.split(':');
    var node = nodeAt(bits[0]);
    return node ? { node: node, offset: parseInt(bits[1] || '0', 10) } : null;
  }

  /** Not counted as letters: spaces, control characters and the invisible ones. The app counts the same way. */
  function uncounted(x) {
    return x <= 0x20 || (x >= 0x7F && x <= 0xA0) || x === 0xAD || x === 0x1680 || (x >= 0x2000 && x <= 0x200F) ||
      (x >= 0x2028 && x <= 0x202F) || (x >= 0x205F && x <= 0x2064) || x === 0x3000 || x === 0xFEFF;
  }

  /** Text that isn't part of what's read, as the app leaves it out too. */
  var NOT_READ = /^(script|style|noscript|template|noembed|noframes|xmp|iframe)$/i;

  /** The [n]th letter of the chapter (from 0), or its last one if it has fewer. */
  function pointAtLetters(n) {
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var k = 0, last = null;
    for (var t = w.nextNode(); t; t = w.nextNode()) {
      var skip = false;
      for (var e = t.parentNode; e && e !== document.body; e = e.parentNode) {
        if (e.nodeType === 1 && NOT_READ.test(e.localName || '')) { skip = true; break; }
      }
      if (skip) continue;
      var d = t.data;
      for (var j = 0; j < d.length; j++) {
        if (uncounted(d.charCodeAt(j))) continue;
        if (k === n) return { node: t, offset: j };
        k++;
        last = { node: t, offset: j };
      }
    }
    return last;
  }

  function snippetFrom(node, offset) {
    var out = '';
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    w.currentNode = node;
    var n = node, first = true;
    while (n && out.length < 160) {
      var t = first ? n.data.slice(offset) : n.data;
      out += t;
      first = false;
      n = w.nextNode();
    }
    return out.replace(/\s+/g, ' ').trim().slice(0, 140);
  }

  function here() {
    var v = firstVisible();
    var anchor = v ? pathOf(v.node) + ':' + v.offset : null;
    // Pictures still loading can make a scrolled chapter longer.
    if (scrolled) countPages();
    return {
      page: page,
      pages: pages,
      progress: scrolled ? scrolledProgress() : pages > 1 ? page / (pages - 1) : 0,
      anchor: anchor,
      snippet: v ? snippetFrom(v.node, v.offset) : '',
      spread: perScreen === 2,
      scrolled: scrolled,
      layout: layoutKey(),
      columns: columns,
      blank: blankBefore,
      display: displayPage,
      frame: { w: W, h: H, perScreen: perScreen, padX: padX, col: colW, padTop: padTop, padBottom: padBottom },
      picture: picturePage,
      spreadPicture: spreadPicture,
    };
  }

  /** What the pages were laid out at: the same chapter comes to the same pages while this stays the same. */
  function layoutKey() {
    return [W, H, perScreen, scrolled ? 1 : 0, padX, padTop + '/' + padBottom, bandTop, bandBottom, settings.fontScale, settings.font,
      settings.line, settings.align, settings.hyphens, settings.lang, settings.printHeads ? 1 : 0, settings.dropCaps ? 1 : 0].join('|');
  }

  var reportTimer = 0, reportedAt = 0;
  function report() {
    if (!ready) return;
    clearTimeout(reportTimer);
    reportTimer = setTimeout(function () {
      reportedAt = Date.now();
      try { KL.position(JSON.stringify(here())); } catch (e) { log(e); }
    }, 60);
  }

  /** While a scrolled chapter moves: where the reader is, now and then, and once it stops. */
  function reportWhileScrolling() {
    if (!ready) return;
    if (Date.now() - reportedAt > 400) {
      reportedAt = Date.now();
      try { KL.position(JSON.stringify(here())); } catch (e) { log(e); }
    }
    report();
  }

  // ---- going places ------------------------------------------------------------------------------

  function elementFor(fragment) {
    if (!fragment) return null;
    var id = decodeURIComponent(fragment);
    return document.getElementById(id) || document.querySelector('[name="' + id.replace(/"/g, '\\"') + '"]');
  }

  /** The first picture in the page (an img, or an svg's image) whose address is [url]. */
  function pictureFor(url) {
    if (!url) return null;
    var plain = function (u) {
      u = String(u).split('#')[0].split('?')[0];
      try { return decodeURI(u); } catch (e) { return u; }
    };
    var want = plain(url);
    var all = document.querySelectorAll('img, image');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var src = el.getAttribute('src') || el.getAttribute('href') || el.getAttribute('xlink:href');
      if (!src) continue;
      try { src = new URL(src, document.baseURI).href; } catch (e) { continue; }
      if (plain(src) === want) return el.tagName.toLowerCase() === 'image' && el.ownerSVGElement ? el.ownerSVGElement : el;
    }
    return null;
  }

  function pageOfElement(el) {
    var target = el;
    // An empty anchor has no box of its own: use what follows it.
    var r = rectOf(target);
    if (!r) {
      var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
      w.currentNode = el;
      var t = w.nextNode();
      while (t && !t.data.trim()) t = w.nextNode();
      if (t) return pageOf(t, 0);
      return null;
    }
    return Math.max(0, Math.min(pages - 1, Math.floor((r.left + scroller.scrollLeft + 1) / W)));
  }

  /** Finds the [occurrence]th match of [text] (case and spacing ignored) and returns a range for it. */
  function findText(text, occurrence, segment, offset) {
    var nodes = textNodes();
    // Exactly where the app says, when the text is still there.
    if (segment != null && segment >= 0 && segment < nodes.length) {
      var n = nodes[segment];
      var got = n.data.substr(offset, text.length);
      if (got.toLowerCase() === text.toLowerCase()) {
        var r0 = document.createRange();
        r0.setStart(n, offset);
        r0.setEnd(n, offset + text.length);
        return r0;
      }
    }
    var wanted = text.replace(/\s+/g, ' ').toLowerCase();
    var flat = '', map = [];
    for (var i = 0; i < nodes.length; i++) {
      var d = nodes[i].data;
      for (var j = 0; j < d.length; j++) {
        var c = d[j];
        if (/\s/.test(c)) {
          if (flat.length && flat[flat.length - 1] === ' ') continue;
          c = ' ';
        }
        flat += c.toLowerCase();
        map.push([i, j]);
      }
    }
    var at = -1, k = -1;
    do { at = flat.indexOf(wanted, at + 1); if (at >= 0) k++; } while (at >= 0 && k < (occurrence || 0));
    if (at < 0) return null;
    var s = map[at], e = map[at + wanted.length - 1];
    var r = document.createRange();
    r.setStart(nodes[s[0]], s[1]);
    r.setEnd(nodes[e[0]], e[1] + 1);
    return r;
  }

  function clearHighlight(name) { try { if (window.CSS && CSS.highlights) CSS.highlights.delete(name); } catch (e) {} }
  function highlight(name, range) {
    try { if (window.CSS && CSS.highlights && window.Highlight) CSS.highlights.set(name, new Highlight(range)); } catch (e) {}
  }

  function flash(range) {
    highlight('kl-flash', range);
    setTimeout(function () { clearHighlight('kl-flash'); }, 1600);
  }

  /** The same places as [goTo], in a scrolled chapter: each put just under the top band. */
  function goToScrolled(t, animate) {
    // The end is the last lines at the foot of the screen, as a chapter's last page would be.
    var y = 0, max = textMax();
    var nearTop = function (top) { return top == null ? null : top - topInset(); };
    switch (t.type) {
      case 'end': y = max; break;
      case 'page': y = (t.value | 0) * stepH(); break;
      case 'progress': y = (t.value || 0) * max; break;
      case 'anchor': {
        var at = placeOf(t.value);
        var a = at ? nearTop(yOf(at.node, at.offset)) : null;
        y = a != null ? a : (t.progress || 0) * max;
        break;
      }
      case 'range': {
        var hr = rangeFor(t);
        var rr = hr ? hr.getClientRects()[0] : null;
        y = rr ? nearTop(rr.top + scroller.scrollTop) : (t.progress || 0) * max;
        if (hr && t.flash) flash(hr);
        break;
      }
      case 'picture': {
        var pic = pictureFor(t.value);
        var pr = pic ? pic.getBoundingClientRect() : null;
        y = pr ? nearTop(pr.top + scroller.scrollTop) : 0;
        break;
      }
      case 'fragment': {
        var el = elementFor(t.value);
        var er = el ? (rectOf(el) || el.getBoundingClientRect()) : null;
        y = er ? nearTop(er.top + scroller.scrollTop) : 0;
        if (el && t.flash) {
          var fr = document.createRange();
          fr.selectNodeContents(el);
          flash(fr);
        }
        break;
      }
      case 'search': {
        var range = findText(t.text || '', t.occurrence || 0, t.segment, t.offset);
        if (range) {
          var sr = range.getClientRects()[0];
          // A found word a little way down the screen, where the eye looks.
          if (sr) y = sr.top + scroller.scrollTop - bandTop - viewH() * 0.3;
          showY(y, false);
          highlight('kl-search', range);
          return;
        }
        break;
      }
      default: y = 0;
    }
    showY(y, !!animate);
  }

  function goTo(t, animate) {
    t = t || {};
    if (scrolled) { goToScrolled(t, animate); return; }
    var target = 0;
    switch (t.type) {
      case 'end': target = pages - 1; break;
      case 'page': target = t.value | 0; break;
      case 'progress': target = Math.round((t.value || 0) * (pages - 1)); break;
      case 'anchor': {
        var at = placeOf(t.value);
        var p = at ? pageOf(at.node, at.offset) : null;
        if (p == null && t.progress != null) p = Math.round(t.progress * (pages - 1));
        target = p || 0;
        break;
      }
      case 'range': {
        var hr = rangeFor(t);
        var hp = hr ? pageOfRange(hr) : null;
        if (hp == null && t.progress != null) hp = Math.round(t.progress * (pages - 1));
        target = hp || 0;
        if (hr && t.flash) {
          highlight('kl-flash', hr);
          setTimeout(function () { clearHighlight('kl-flash'); }, 1600);
        }
        break;
      }
      case 'picture': {
        var pic = pictureFor(t.value);
        var pp = pic ? pageOfElement(pic) : null;
        target = pp == null ? 0 : pp;
        break;
      }
      case 'fragment': {
        var el = elementFor(t.value);
        var pe = el ? pageOfElement(el) : null;
        target = pe == null ? 0 : pe;
        if (el && t.flash) {
          var fr = document.createRange();
          fr.selectNodeContents(el);
          highlight('kl-flash', fr);
          setTimeout(function () { clearHighlight('kl-flash'); }, 1600);
        }
        break;
      }
      case 'search': {
        var range = findText(t.text || '', t.occurrence || 0, t.segment, t.offset);
        if (range) {
          var rr = range.getClientRects()[0];
          if (rr) target = Math.max(0, Math.min(pages - 1, Math.floor((rr.left + scroller.scrollLeft + 1) / W)));
          showPage(target, false);
          highlight('kl-search', range);
          return;
        }
        break;
      }
      default: target = 0;
    }
    showPage(target, !!animate);
  }

  // ---- highlights and notes ---------------------------------------------------------------------

  var HL = { yellow: [255, 212, 0], green: [100, 200, 90], blue: [90, 160, 255], pink: [255, 110, 170] };
  var annotations = [];
  var flatCache = null;
  var XHTML = 'http://www.w3.org/1999/xhtml';

  function norm(t) { return String(t || '').replace(/\s+/g, ' ').trim(); }

  /** The chapter's text with runs of space as one, and where each character came from. */
  function flatText() {
    if (flatCache) return flatCache;
    var nodes = textNodes(), flat = '', map = [];
    for (var i = 0; i < nodes.length; i++) {
      var d = nodes[i].data;
      for (var j = 0; j < d.length; j++) {
        var c = d[j];
        if (/\s/.test(c)) {
          if (flat.length && flat[flat.length - 1] === ' ') continue;
          c = ' ';
        }
        flat += c.toLowerCase();
        map.push([nodes[i], j]);
      }
    }
    flatCache = { flat: flat, map: map };
    return flatCache;
  }

  /** How many times these words occur in the chapter before this range: to find them again after an edit. */
  function occurrenceOf(range, text) {
    var f = flatText(), wanted = norm(text).toLowerCase();
    if (!wanted) return 0;
    var at = -1, k = 0;
    while ((at = f.flat.indexOf(wanted, at + 1)) >= 0) {
      var m = f.map[at];
      var probe = document.createRange();
      try { probe.setStart(m[0], m[1]); } catch (e) { break; }
      if (probe.compareBoundaryPoints(Range.START_TO_START, range) >= 0) break;
      k++;
    }
    return k;
  }

  function pointAt(anchor) {
    var bits = String(anchor || '').split(':');
    var n = nodeAt(bits[0]);
    if (!n) return null;
    var off = parseInt(bits[1] || '0', 10);
    var max = n.nodeType === 3 ? n.data.length : n.childNodes.length;
    return off <= max ? { node: n, offset: off } : null;
  }

  /** Where a highlight is: by its anchors while the words there still match, else by its words. */
  function rangeFor(a) {
    var s = pointAt(a.start), e = pointAt(a.end);
    if (s && e) {
      try {
        var r = document.createRange();
        r.setStart(s.node, s.offset);
        r.setEnd(e.node, e.offset);
        if (norm(r.toString()).toLowerCase() === norm(a.text).toLowerCase()) return r;
      } catch (er) {}
    }
    return a.text ? findText(a.text, a.occurrence || 0) : null;
  }

  function highlightStyles() {
    var dark = !!settings.dimImages;
    var css = '';
    for (var c in HL) {
      var rgb = HL[c].join(',');
      css += '::highlight(kl-hl-' + c + '){background-color:rgba(' + rgb + ',' + (dark ? 0.34 : 0.42) + ');}';
      css += '::highlight(kl-ul-' + c + '){text-decoration-line:underline;text-decoration-color:rgb(' + rgb + ');text-decoration-thickness:3px;}';
    }
    css += '#kl-marks{position:absolute;left:0;top:0;width:0;height:0;overflow:visible;z-index:5;}';
    css += '.kl-mark{position:absolute;width:13px;height:13px;margin:-4px 0 0 1px;border-radius:50% 50% 50% 0;box-shadow:0 0 0 1.5px ' + (dark ? 'rgba(0,0,0,.6)' : 'rgba(255,255,255,.9)') + ';cursor:pointer;}';
    var el = document.getElementById('kl-hl-style');
    if (!el) {
      el = document.createElementNS(XHTML, 'style');
      el.id = 'kl-hl-style';
      (document.head || document.documentElement).appendChild(el);
    }
    el.textContent = css;
  }

  /** Shows this chapter's highlights: [list] from the app, each with id, anchors, words, colour, style, note. */
  function applyAnnotations(list) {
    annotations = [];
    var groups = {};
    for (var i = 0; i < (list || []).length; i++) {
      var a = list[i];
      var r = rangeFor(a);
      if (!r) continue;
      a.range = r;
      annotations.push(a);
      var c = HL[a.color] ? a.color : 'yellow';
      var key = (a.style === 'underline' ? 'kl-ul-' : 'kl-hl-') + c;
      (groups[key] = groups[key] || []).push(r);
    }
    for (var name in HL) { clearHighlight('kl-hl-' + name); clearHighlight('kl-ul-' + name); }
    try {
      if (window.CSS && CSS.highlights && window.Highlight) {
        for (var k in groups) {
          var h = new Highlight();
          for (var j = 0; j < groups[k].length; j++) h.add(groups[k][j]);
          CSS.highlights.set(k, h);
        }
      }
    } catch (e) { log(e); }
    placeNoteMarks();
  }

  /** A small flag at the end of each passage that has a note. */
  function placeNoteMarks() {
    var box = document.getElementById('kl-marks');
    if (box) box.parentNode.removeChild(box);
    var noted = annotations.filter(function (a) { return a.note; });
    if (!noted.length) return;
    box = document.createElementNS(XHTML, 'div');
    box.id = 'kl-marks';
    for (var i = 0; i < noted.length; i++) {
      var rs = noted[i].range.getClientRects();
      if (!rs.length) continue;
      var q = rs[rs.length - 1];
      var m = document.createElementNS(XHTML, 'div');
      m.className = 'kl-mark';
      m.setAttribute('data-id', noted[i].id);
      var rgb = (HL[noted[i].color] || HL.yellow).join(',');
      m.style.background = 'rgb(' + rgb + ')';
      m.style.left = (q.right + scroller.scrollLeft) + 'px';
      m.style.top = (q.top + scroller.scrollTop) + 'px';
      box.appendChild(m);
    }
    // Outside the chapter's body, so nothing in it moves.
    document.documentElement.appendChild(box);
  }

  function rectJson(r) { return { x: r.left, y: r.top, w: r.width, h: r.height }; }

  /** The page a range starts on. */
  function pageOfRange(r) {
    var rs = r.getClientRects();
    for (var i = 0; i < rs.length; i++) {
      if (rs[i].width || rs[i].height) return Math.max(0, Math.min(pages - 1, Math.floor((rs[i].left + scroller.scrollLeft + 1) / W)));
    }
    return null;
  }

  /** The highlight under a tap, if any. */
  function annotationAt(x, y) {
    for (var i = annotations.length - 1; i >= 0; i--) {
      var rs = annotations[i].range.getClientRects();
      for (var k = 0; k < rs.length; k++) {
        var q = rs[k];
        if (x >= q.left - 2 && x <= q.right + 2 && y >= q.top - 3 && y <= q.bottom + 3) return annotations[i];
      }
    }
    return null;
  }

  function annotationById(id) {
    for (var i = 0; i < annotations.length; i++) if (annotations[i].id === id) return annotations[i];
    return null;
  }

  /** What's selected, for the app to highlight or look up: anchors, words, and where it is on screen. */
  function selectionInfo() {
    var sel = window.getSelection && window.getSelection();
    if (!sel || !sel.rangeCount || sel.isCollapsed) return null;
    var r = sel.getRangeAt(0);
    if (!document.body.contains(r.commonAncestorContainer) && r.commonAncestorContainer !== document.body) return null;
    var text = r.toString();
    if (!norm(text)) return null;
    var start = pathOf(r.startContainer), end = pathOf(r.endContainer);
    if (start == null || end == null) return null;
    var progress = 0;
    if (scrolled) {
      var r0 = r.getClientRects()[0];
      progress = r0 && textMax() > 0 ? Math.max(0, Math.min(1, (r0.top + scroller.scrollTop - topInset()) / textMax())) : scrolledProgress();
    } else {
      var page0 = pageOfRange(r);
      progress = pages > 1 && page0 != null ? page0 / (pages - 1) : 0;
    }
    return {
      start: start + ':' + r.startOffset,
      end: end + ':' + r.endOffset,
      text: text,
      occurrence: occurrenceOf(r, text),
      progress: progress,
      rect: rectJson(r.getBoundingClientRect()),
      lines: lineRects(r),
      para: paragraphOf(r),
    };
  }

  /** Where a selection's words are on the screen, a box for each line of them (the first dozen). */
  function lineRects(r) {
    var list = r.getClientRects(), out = [];
    for (var i = 0; i < list.length; i++) {
      var q = list[i];
      if (q.width < 1 || q.height < 1) continue;
      var joined = false;
      for (var j = 0; j < out.length && !joined; j++) {
        var o = out[j];
        // On the same line: their heights mostly overlap.
        var overlap = Math.min(o.y + o.h, q.bottom) - Math.max(o.y, q.top);
        if (overlap > Math.min(o.h, q.height) * 0.6) {
          var left = Math.min(o.x, q.left), right = Math.max(o.x + o.w, q.right);
          var top = Math.max(o.y, q.top), bottom = Math.min(o.y + o.h, q.bottom);
          o.x = left; o.w = right - left; o.y = top; o.h = bottom - top;
          joined = true;
        }
      }
      if (!joined && out.length < 12) out.push({ x: q.left, y: q.top, w: q.width, h: q.height });
    }
    return out;
  }

  /** The words of the paragraph a selection is in, for looking a word up as it's used there. */
  function paragraphOf(r) {
    var n = r.commonAncestorContainer;
    if (n && n.nodeType !== 1) n = n.parentNode;
    while (n && n !== document.body && !/^(p|div|li|blockquote|td|th|dd|dt|h[1-6]|section|article)$/i.test(n.localName || '')) n = n.parentNode;
    var t = n && n !== document.documentElement ? (n.textContent || '') : '';
    t = t.replace(/\s+/g, ' ').trim();
    if (t.length <= 2400) return t;
    // A long block (a chapter in one div): the words round the selection.
    var at = t.indexOf(r.toString().replace(/\s+/g, ' ').trim());
    if (at < 0) at = 0;
    return t.substring(Math.max(0, at - 800), Math.min(t.length, at + 1200));
  }

  // ---- laying out, and again when something changes -----------------------------------------------

  /**
   * After the next frame. Counting, in a WebView that's never drawn, there may be no frames to wait
   * for (they come with drawing), so a moment instead: the layout is there whether or not it's drawn.
   */
  function nextFrame(fn) {
    if (countingOnly) setTimeout(fn, 16);
    else requestAnimationFrame(fn);
  }

  function whenSettled(done) {
    var waits = [];
    if (document.fonts && document.fonts.ready) waits.push(document.fonts.ready);
    var imgs = document.images;
    for (var i = 0; i < imgs.length; i++) {
      if (!imgs[i].complete) {
        waits.push(new Promise(function (res) { imgs[i].addEventListener('load', res); imgs[i].addEventListener('error', res); }));
      }
    }
    var timeout = new Promise(function (res) { setTimeout(res, 1800); });
    Promise.race([Promise.all(waits), timeout]).then(function () {
      nextFrame(function () { nextFrame(done); });
    });
  }

  /**
   * Where the last relayout left the reader: the words it kept to, and the page (or the scroll) they came
   * to. While the reader stays there, the next relayout keeps to the same words, so changing one setting
   * after another doesn't creep back a page each time (the first words on a page after a relayout are
   * usually a little before the ones kept to).
   */
  var kept = null;

  /** Lays out again (new settings, a new screen size) and stays at the same words. */
  function relayout(change) {
    var still = ready && kept && kept.scrolled === scrolled && document.body.contains(kept.keep.node) &&
        (scrolled ? Math.abs(scroller.scrollTop - kept.at) <= 2 : page === kept.at);
    var keep = still ? kept.keep : ready ? firstVisible() : null;
    // Scrolling: how far down the screen those words were, so they stay there. (Put just under the top
    // band instead, the line half under it would come down a line each time.)
    var wasScrolled = scrolled, keptAt = null;
    if (keep && scrolled) {
      var kr = rectOf(keep.node, keep.offset);
      if (kr) keptAt = kr.top;
    }
    var spentOnPage = autoPageDone;
    var progress = scrolled ? scrolledProgress() : pages > 1 ? page / (pages - 1) : 0;
    change();
    whenSettled(function () {
      setScroll(0);
      if (!scrolled) scroller.scrollTop = 0;
      try { unmarkSmallPictures(); fitPictureBoxes(); } catch (e) { log(e); }
      measureDensity();
      countPages();
      if (scrolled) {
        var y = keep ? yOf(keep.node, keep.offset) : null;
        var at = wasScrolled && keptAt != null ? keptAt : topInset();
        showY(y == null ? progress * textMax() : y - at, false);
      } else {
        var p = keep ? pageOf(keep.node, keep.offset) : null;
        showPage(p == null ? Math.round(progress * (pages - 1)) : p, false);
      }
      kept = keep ? { keep: keep, scrolled: scrolled, at: scrolled ? scroller.scrollTop : page } : null;
      placeNoteMarks();
      blendPictures();
      root.classList.toggle('kl-auto-pages', auto.on && !scrolled);
      if (auto.on) {
        autoPos = -1;
        startPageTimer();
        autoPageDone = Math.min(spentOnPage, autoPageNeed);
      }
    });
  }

  // ---- the end of a scrolled chapter --------------------------------------------------------------

  /**
   * Under a scrolled chapter: where it ends, and the way on to the next one. It's tall enough for the
   * last lines to be scrolled up to where the eye reads. Outside the body, so the chapter is unchanged.
   */
  /** The reader's own words, in the language KoLib is in (sent with the chapter), or the English. */
  var words = {};
  function word(key, english) { return (words && words[key]) || english; }

  function addChapterEnd() {
    if (document.getElementById('kl-end')) return;
    var box = document.createElementNS(XHTML, 'div');
    box.id = 'kl-end';
    var label = document.createElementNS(XHTML, 'div');
    label.className = 'kl-end-label';
    label.textContent = isLast ? word('endOfBook', 'End of book') : word('endOfChapter', 'End of chapter');
    box.appendChild(label);
    if (!isLast) {
      var go = document.createElementNS(XHTML, 'div');
      go.id = 'kl-end-go';
      go.textContent = nextTitle
        ? word('nextChapterTitled', 'Next chapter: {0}').replace('{0}', function () { return nextTitle; })
        : word('nextChapter', 'Next chapter');
      box.appendChild(go);
      var hint = document.createElementNS(XHTML, 'div');
      hint.className = 'kl-end-hint';
      hint.textContent = word('keepReading', 'Tap the button or pull up to keep reading');
      box.appendChild(hint);
    }
    root.appendChild(box);
  }

  // Pulling a scrolled chapter on past its end (or back past its start) opens the next (or previous) one.
  var pull = null, hasPrev = true;
  function pullThreshold() { return Math.max(70, Math.min(140, H * 0.1)); }

  function pullMove(t) {
    var max = maxScroll();
    var atEnd = scroller.scrollTop >= max - 1 && !isLast;
    var atStart = scroller.scrollTop <= 1 && hasPrev;
    if (atEnd && atStart) {
      // A chapter too short to scroll (a dedication, a title page): it's at both ends at once, so the way
      // the finger goes says which way it pulls.
      if (!pull) pull = { dir: 0, from: t.clientY, dist: 0 };
      var d = t.clientY - pull.from;
      pull.dir = d < 0 ? 1 : -1;
      pull.dist = Math.abs(d);
    } else if (atEnd) {
      if (!pull || pull.dir !== 1) pull = { dir: 1, from: t.clientY, dist: 0 };
      pull.dist = Math.max(0, pull.from - t.clientY);
    } else if (scroller.scrollTop <= 1 && hasPrev) {
      if (!pull || pull.dir !== -1) pull = { dir: -1, from: t.clientY, dist: 0 };
      pull.dist = Math.max(0, t.clientY - pull.from);
    } else pull = null;
    showPull();
  }

  function pullEnd() {
    var p = pull;
    pull = null;
    showPull();
    if (p && p.dist >= pullThreshold()) KL.edge(p.dir);
  }

  function showPull() {
    var el = document.getElementById('kl-pull');
    if (!pull || pull.dist < 14) {
      if (el) el.classList.remove('kl-on');
      return;
    }
    if (!el) {
      el = document.createElementNS(XHTML, 'div');
      el.id = 'kl-pull';
      root.appendChild(el);
    }
    var far = pull.dist >= pullThreshold();
    el.className = 'kl-on ' + (pull.dir > 0 ? 'kl-bottom' : 'kl-top');
    el.textContent = pull.dir > 0
      ? (far ? word('letGoNext', 'Let go to open the next chapter') : word('pullNext', 'Pull up for the next chapter'))
      : (far ? word('letGoPrevious', 'Let go to open the previous chapter') : word('pullPrevious', 'Pull down for the previous chapter'));
  }

  /** Runs [done] once the WebView has a size (it can load a page before it's been laid out). */
  function whenSized(done) {
    if (hasSize()) { done(); return; }
    var t0 = Date.now();
    (function wait() {
      if (hasSize() || Date.now() - t0 > 4000) done();
      else nextFrame(wait);
    })();
  }

  function start() {
    var st = parse(KL.start());
    if (st.shell) { try { KL.counterReady(); } catch (e) { log(e); } return; }
    if (st.measure) { countOnly(st); return; }
    place = st.place || null;
    chapterOpening = st.opening !== false;
    try { applyTurns(st.turns); } catch (e) { log(e); }
    markFullPagePictures();
    try { keepPictureShapes(); } catch (e) { log(e); }
    try { centrePictures(); } catch (e) { log(e); }
    try { markLinkWrappers(); } catch (e) { log(e); }
    try { keepOpeningTogether(); } catch (e) { log(e); }
    words = st.words || {};
    var around = st.chapters || {};
    nextTitle = around.next || null;
    isLast = around.hasNext === false;
    hasPrev = around.hasPrev !== false;
    try { addChapterEnd(); } catch (e) { log(e); }
    startTarget = st.target || { type: 'start' };
    whenSized(function () {
      applySettings(st.settings || {});
      bindTouchMove();
      whenSettled(function () {
        // The size may have settled while pictures and fonts loaded.
        if (screenWidth() !== W || screenHeight() !== H) measure();
        try { unmarkSmallPictures(); fitPictureBoxes(); } catch (e) { log(e); }
        try { markOpening(); } catch (e) { log(e); }
        measureDensity();
        countPages();
        blendPictures();
        ready = true;
        try { applyAnnotations(st.annotations || []); } catch (e) { log(e); }
        goTo(startTarget, false);
        root.classList.add('kl-ready');
        var h = here();
        try {
          KL.ready(JSON.stringify({ pages: pages, page: page, spread: perScreen === 2, progress: h.progress, scrolled: scrolled, columns: columns,
            blank: blankBefore, display: displayPage, picture: picturePage, spreadPicture: spreadPicture }));
        } catch (e) { log(e); }
        if (st.auto) setAuto(st.auto, true);
      });
    });
  }

  /**
   * Lays the chapter out just as start() does, with the same settings and at the same size, counts its
   * pages, and tells the app. Nothing else: no place to go to, nothing to show. The app numbers pages
   * through the whole book from these counts, so the numbers don't change as the book is read.
   */
  function countOnly(st) {
    readyToCount(st);
    whenSized(function () {
      applySettings(st.settings || {});
      settleForCount(null, function () { finishCount(st.measure); });
    });
  }

  /** What countOnly does before the page is laid out: as start() does it, for a chapter just put in. */
  function readyToCount(st) {
    countingOnly = true;
    place = st.place || null;
    chapterOpening = st.opening !== false;
    // As a fresh page has them: what the chapter counted before found doesn't carry over.
    picturePage = false;
    blankBefore = 0;
    spreadPicture = false;
    displayPage = false;
    try { applyTurns(st.turns); } catch (e) { log(e); }
    markFullPagePictures();
    try { keepPictureShapes(); } catch (e) { log(e); }
    try { centrePictures(); } catch (e) { log(e); }
    try { markLinkWrappers(); } catch (e) { log(e); }
    try { keepOpeningTogether(); } catch (e) { log(e); }
    words = st.words || {};
    var around = st.chapters || {};
    nextTitle = around.next || null;
    isLast = around.hasNext === false;
    hasPrev = around.hasPrev !== false;
    try { addChapterEnd(); } catch (e) { log(e); }
  }

  function finishCount(id) {
    if (screenWidth() !== W || screenHeight() !== H) measure();
    try { unmarkSmallPictures(); fitPictureBoxes(); } catch (e) { log(e); }
    try { markOpening(); } catch (e) { log(e); }
    measureDensity();
    countPages();
    try { KL.measured(JSON.stringify({ id: id, pages: pages, columns: columns, layout: layoutKey() })); } catch (e) { log(e); }
  }

  /**
   * Counting: once the page's style sheets ([waits]), pictures and fonts are in. No frames are waited for
   * (none are drawn): the layout is worked out when it's asked for. A font is only fetched once text that
   * needs it is laid out, so the layout is asked for first, and again after anything has loaded. A count
   * waits for all of it, however long it takes (up to a point), so it comes out the same every time.
   */
  function settleForCount(waits, done) {
    var t0 = Date.now(), rounds = 0, first = waits || [];
    (function round() {
      try { void scroller.scrollWidth; } catch (e) {}
      var pending = first;
      first = [];
      var imgs = document.images;
      for (var i = 0; i < imgs.length; i++) {
        var im = imgs[i];
        if (!im.complete) pending.push(new Promise(function (res) { im.addEventListener('load', res); im.addEventListener('error', res); }));
      }
      if (document.fonts && document.fonts.status === 'loading') pending.push(document.fonts.ready);
      if (!pending.length || rounds >= 6 || Date.now() - t0 > 10000) { done(); return; }
      rounds++;
      var left = Math.max(0, 10000 - (Date.now() - t0));
      Promise.race([Promise.all(pending), new Promise(function (res) { setTimeout(res, left); })]).then(round);
    })();
  }

  // ---- counting one chapter after another in the one page ----------------------------------------

  // The book's own head (its style sheets) as the chapter being counted has it: kept for the next
  // chapter if it has the same, as most do, so they're not loaded and read again.
  var shellHead = [], shellHeadKey = null;

  /**
   * The page the app counts a book's pages in: one chapter after another is put into it in place of the
   * last, laid out and counted just as a chapter opened on its own is (see countOnly), without loading
   * the reader's script, styles and fonts again for each. The chapter is fetched as the reader gets it,
   * read by the same parser, and its head, its root's attributes and everything else in it put in this
   * page's place. The app is told the count, or which kind of page the chapter needs (XHTML or HTML: this
   * page is one or the other), or that it couldn't be counted here.
   */
  function countInShell(id, url, st) {
    var tell = function (o) { o.id = id; try { KL.measured(JSON.stringify(o)); } catch (e) { log(e); } };
    var xml = document.contentType === 'application/xhtml+xml';
    fetch(url, { cache: 'no-store' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      var type = (r.headers.get('Content-Type') || 'text/html').split(';')[0].trim().toLowerCase();
      return r.text().then(function (text) { return { type: type, text: text }; });
    }).then(function (got) {
      var wantXml = got.type === 'application/xhtml+xml';
      if (wantXml !== xml) { tell({ shell: wantXml ? 'xhtml' : 'html' }); return; }
      var doc = new DOMParser().parseFromString(got.text, wantXml ? 'application/xhtml+xml' : 'text/html');
      if (!doc || !doc.documentElement || doc.documentElement.localName !== 'html' || doc.getElementsByTagName('parsererror').length) {
        tell({ failed: 'parse' });
        return;
      }
      var sheets = swapIn(doc, url);
      pagePath = new URL(url, location.href).pathname;
      readyToCount(st);
      setScroll(0);
      scroller.scrollTop = 0;
      applySettings(st.settings || {});
      settleForCount(sheets, function () { finishCount(id); });
    }).catch(function (e) { log(e); tell({ failed: String(e && e.message || e) }); });
  }

  /** Puts the chapter [doc] (fetched from [url]) in this page's place; the style sheets now loading. */
  function swapIn(doc, url) {
    var head = document.head;
    var base = document.getElementById('kl-base');
    base.setAttribute('href', url.split('#')[0]);
    // The book's head: its style sheets and the rest, in order (the reader's own come after them).
    var from = doc.head, own = [];
    if (from) for (var n = from.firstChild; n; n = n.nextSibling) {
      if (n.nodeType !== 1 || /^(script|title|base)$/i.test(n.localName)) continue;
      if (n.localName === 'meta' && /viewport/i.test(n.getAttribute('name') || '')) continue;
      if (n.localName === 'link' && (n.getAttribute('href') || '').indexOf('/app/reader/') >= 0) continue;
      own.push(n);
    }
    var key = own.map(function (e) { return e.outerHTML; }).join('\n');
    var loading = [];
    if (key !== shellHeadKey) {
      shellHead.forEach(function (e) { if (e.parentNode) e.parentNode.removeChild(e); });
      shellHead = [];
      var after = base.nextSibling;
      own.forEach(function (e) {
        var c = document.importNode(e, true);
        if (c.localName === 'link' && /stylesheet/i.test(c.getAttribute('rel') || '')) {
          loading.push(new Promise(function (res) { c.addEventListener('load', res); c.addEventListener('error', res); }));
        }
        head.insertBefore(c, after);
        shellHead.push(c);
      });
      shellHeadKey = key;
    }
    // The root: the chapter's own attributes (the reader's classes and settings go on again as it's counted).
    var root0 = document.documentElement, src = doc.documentElement;
    while (root0.attributes.length) root0.removeAttributeNode(root0.attributes[0]);
    for (var i = 0; i < src.attributes.length; i++) {
      var a = src.attributes[i];
      try {
        if (a.namespaceURI) root0.setAttributeNS(a.namespaceURI, a.name, a.value);
        else root0.setAttribute(a.name, a.value);
      } catch (e) { log(e); }
    }
    // Everything else in it: the body, and a box the app put in front of it.
    for (var c = root0.firstChild; c;) {
      var nx = c.nextSibling;
      if (c !== head) root0.removeChild(c);
      c = nx;
    }
    for (var s2 = src.firstChild; s2; s2 = s2.nextSibling) {
      if (s2 === from) continue;
      root0.appendChild(document.importNode(s2, true));
    }
    return loading;
  }

  var resizeTimer = 0;
  window.addEventListener('resize', function () {
    if (!ready) return;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (screenWidth() === laidOutWidth && screenHeight() === H) return;
      relayout(measure);
    }, 120);
  });

  // ---- touch, taps, keys --------------------------------------------------------------------------

  var touch = null, suppressClick = false;

  function selectionActive() {
    var s = window.getSelection && window.getSelection();
    return !!(s && s.type === 'Range' && String(s).length);
  }

  document.addEventListener('touchstart', function (e) {
    autoTouch(true);
    if (!ready || e.touches.length !== 1) { touch = null; return; }
    suppressClick = selectionActive();
    var t = e.touches[0];
    cancelAnimation();
    touch = { x: t.clientX, y: t.clientY, t: Date.now(), base: page * W, moved: false, lastX: t.clientX, lastT: Date.now(), v: 0 };
    pull = null;
  }, { passive: true });

  function onTouchMove(e) {
    if (!touch || selectionActive()) return;
    var t = e.touches[0];
    var dx = t.clientX - touch.x, dy = t.clientY - touch.y;
    if (scrolled) {
      // The page scrolls by itself; this only watches for a pull past either end.
      if (!touch.moved && Math.abs(dy) > 10) touch.moved = true;
      pullMove(t);
      return;
    }
    if (!touch.moved) {
      if (Math.abs(dx) < 12 || Math.abs(dx) < Math.abs(dy) * 1.2) return;
      touch.moved = true;
    }
    if (e.cancelable) e.preventDefault();
    var now = Date.now();
    touch.v = (t.clientX - touch.lastX) / Math.max(1, now - touch.lastT);
    touch.lastX = t.clientX;
    touch.lastT = now;
    // With Curl, Sweep or Fade the app turns the page with the finger; if it couldn't, the turn comes when it lets go.
    if (appTurns()) return;
    var x = touch.base - dx;
    var min = 0, max = (pages - 1) * W;
    // Past the first or last page it pulls with resistance, to show there's another chapter.
    if (x < min) x = min - Math.min(W * 0.3, (min - x) * 0.35);
    if (x > max) x = max + Math.min(W * 0.3, (x - max) * 0.35);
    setScroll(Math.round(x));
  }

  // Pages: the swipe is handled here, so it can't be passive. Scrolling: the browser scrolls, and a
  // passive listener doesn't hold it up.
  var movePassive = null;
  function bindTouchMove() {
    if (movePassive === scrolled) return;
    if (movePassive !== null) document.removeEventListener('touchmove', onTouchMove);
    document.addEventListener('touchmove', onTouchMove, { passive: scrolled });
    movePassive = scrolled;
  }

  document.addEventListener('touchend', function (e) {
    if (!e.touches || !e.touches.length) autoTouch(false);
    if (!touch) return;
    var t = touch;
    touch = null;
    if (scrolled) {
      if (t.moved) suppressClick = true;
      // Set to keep going: after a drag, a moment for any glide to begin, then on it goes.
      if (t.moved && auto.on && keepsGoing()) autoWaitUntil = Math.max(autoWaitUntil, performance.now() + 250);
      pullEnd();
      return;
    }
    if (!t.moved) return;
    suppressClick = true;
    var dx = t.lastX - t.x;
    var fling = Math.abs(t.v) > 0.35;
    var far = Math.abs(dx) > W * 0.18;
    if (appTurns()) {
      if ((far || fling) && dx < 0) next(t.y / H);
      else if ((far || fling) && dx > 0) prev(t.y / H);
      return;
    }
    if ((far || fling) && dx < 0) {
      if (page < pages - 1) showPage(page + 1, true);
      else { showPage(page, true); KL.edge(1); }
    } else if ((far || fling) && dx > 0) {
      if (page > 0) showPage(page - 1, true);
      else { showPage(page, true); KL.edge(-1); }
    } else {
      showPage(page, true);
    }
  }, { passive: true });

  // A tap's click is taken unless text is selected (a touch says so first; a mouse or pen here).
  document.addEventListener('mousedown', function () { if (!touch) suppressClick = selectionActive(); }, true);

  // The app took the touch over (it's turning the page with the finger): forget this one.
  document.addEventListener('touchcancel', function (e) {
    if (!e.touches || !e.touches.length) autoTouch(false);
    touch = null;
    suppressClick = false;
    pull = null;
    showPull();
  }, { passive: true });

  // The app needs to know when text is selected: a drag then moves the selection, not the page.
  var selectionReported = false;
  document.addEventListener('selectionchange', function () {
    var on = selectionActive();
    if (on !== selectionReported) {
      selectionReported = on;
      try { KL.selecting(on); } catch (e) {}
    }
  });

  function isNoteLink(a) {
    var type = (a.getAttributeNS('http://www.idpf.org/2007/ops', 'type') || a.getAttribute('epub:type') || '') + ' ' + (a.getAttribute('role') || '');
    if (/noteref/.test(type)) return true;
    var text = (a.textContent || '').trim();
    if (text.length && text.length <= 5 && /^[\[(]?([0-9]{1,3}|[*†‡§¶#]{1,3}|[a-z])[\])]?$/i.test(text)) return true;
    var p = a.parentNode;
    return !!(p && p.nodeType === 1 && /^sup$/i.test(p.localName) && text.length <= 6);
  }

  function noteHtml(doc, id) {
    var el = doc.getElementById(id) || doc.querySelector('[name="' + id + '"]');
    if (!el) return null;
    var box = el;
    // An empty marker: the note is the block it sits in.
    if (!(el.textContent || '').trim() || /^a$/i.test(el.localName)) {
      var b = el.closest ? el.closest('p, li, aside, div, dd, section, blockquote, td') : null;
      if (b) box = b;
    }
    var clone = box.cloneNode(true);
    var scripts = clone.querySelectorAll ? clone.querySelectorAll('script, style') : [];
    for (var i = 0; i < scripts.length; i++) scripts[i].parentNode.removeChild(scripts[i]);
    var html = clone.innerHTML !== undefined ? clone.innerHTML : new XMLSerializer().serializeToString(clone);
    var text = (box.textContent || '').trim();
    if (!text || text.length > 4000) return null;
    return html;
  }

  function handleLink(a) {
    var href = a.getAttribute('href') || a.getAttributeNS('http://www.w3.org/1999/xlink', 'href');
    if (!href) return;
    var url;
    try { url = new URL(href, document.baseURI); } catch (e) { return; }
    if (url.origin !== location.origin) { KL.external(url.href); return; }
    var fragment = url.hash ? url.hash.slice(1) : '';
    var samePage = url.pathname === location.pathname;
    var from = JSON.stringify(here());
    if (isNoteLink(a) && fragment) {
      if (samePage) {
        var html = noteHtml(document, decodeURIComponent(fragment));
        if (html) { KL.note(JSON.stringify({ html: html, href: url.href })); return; }
      } else {
        fetch(url.href.split('#')[0]).then(function (r) {
          var type = (r.headers.get('content-type') || 'text/html').split(';')[0];
          return r.text().then(function (t) { return new DOMParser().parseFromString(t, type === 'application/xhtml+xml' ? type : 'text/html'); });
        }).then(function (doc) {
          var h = noteHtml(doc, decodeURIComponent(fragment));
          if (h) KL.note(JSON.stringify({ html: h, href: url.href }));
          else KL.link(JSON.stringify({ href: url.href, from: from }));
        }).catch(function () { KL.link(JSON.stringify({ href: url.href, from: from })); });
        return;
      }
    }
    if (samePage) {
      KL.jumped(from);
      goTo({ type: 'fragment', value: fragment, flash: true }, false);
    } else {
      KL.link(JSON.stringify({ href: url.href, from: from }));
    }
  }

  document.addEventListener('click', function (e) {
    if (!ready) return;
    if (suppressClick) { suppressClick = false; e.preventDefault(); return; }
    var t = e.target;
    var a = t && t.closest ? t.closest('a') : null;
    if (a && (a.hasAttribute('href') || a.hasAttributeNS('http://www.w3.org/1999/xlink', 'href'))) {
      e.preventDefault();
      handleLink(a);
      return;
    }
    var mark = t && t.closest ? t.closest('.kl-mark') : null;
    var hit = mark ? annotationById(mark.getAttribute('data-id')) : annotationAt(e.clientX, e.clientY);
    if (hit && !selectionActive()) {
      e.preventDefault();
      try { KL.annotation(JSON.stringify({ id: hit.id, rect: rectJson(hit.range.getBoundingClientRect()) })); } catch (er) { log(er); }
      return;
    }
    if (t && t.closest && t.closest('#kl-end-go')) {
      e.preventDefault();
      KL.edge(1);
      return;
    }
    var img = t && t.closest ? t.closest('img, image') : null;
    // A picture fitted to the page (a cover, a plate) is drawn smaller than its box: a tap in the space beside it
    // isn't on it, but a tap on the page, for the controls or to turn it.
    if (img && img.localName === 'img' && !onDrawnPicture(img, e.clientX, e.clientY)) img = null;
    if (img && placedHalves.length === 2 && img.classList && img.classList.contains('kl-spread-half')) {
      // Half of a map across two pages: the app shows the whole of it.
      try {
        KL.image(placedHalves.map(function (hv) { return new URL(hv.el.currentSrc || hv.el.src, document.baseURI).href; }).join('\n') +
          '\n' + placedHalves[0].cut + '\n' + placedHalves[1].cut);
      } catch (er) {}
      e.preventDefault();
      return;
    }
    if (img) {
      var src = img.currentSrc || img.src || img.getAttribute('href') || img.getAttributeNS('http://www.w3.org/1999/xlink', 'href');
      if (src && typeof src === 'object' && src.baseVal) src = src.baseVal;
      if (src) {
        try { KL.image(new URL(src, document.baseURI).href); } catch (er) {}
        e.preventDefault();
        return;
      }
    }
    var x = e.clientX, y = H > 0 ? e.clientY / H : 0.8;
    var zone = tapZone(x, e.clientY);
    if (zone < 0) prev(y);
    else if (zone > 0) next(y);
    else KL.tap('center');
  }, true);

  /**
   * The point (x, y) is on [img]'s picture as drawn: a picture fitted into its box (object-fit "contain", as covers
   * and plates are) is drawn smaller than the box, where object-position puts it (the middle, mostly).
   */
  function onDrawnPicture(img, x, y) {
    var r = img.getBoundingClientRect();
    var left = r.left, top = r.top, w = r.width, h = r.height;
    var cs = window.getComputedStyle ? getComputedStyle(img) : null;
    var fit = cs ? cs.objectFit : '';
    if ((fit === 'contain' || fit === 'scale-down') && img.naturalWidth && img.naturalHeight && r.width && r.height) {
      var k = Math.min(r.width / img.naturalWidth, r.height / img.naturalHeight);
      if (fit === 'scale-down') k = Math.min(k, 1);
      w = img.naturalWidth * k;
      h = img.naturalHeight * k;
      var pos = ((cs && cs.objectPosition) || '50% 50%').trim().split(/\s+/);
      var at = function (v, free) {
        if (!v || v === 'center') return free / 2;
        if (/%$/.test(v)) return free * parseFloat(v) / 100;
        if (/px$/.test(v)) return parseFloat(v);
        if (v === 'left' || v === 'top') return 0;
        if (v === 'right' || v === 'bottom') return free;
        return free / 2;
      };
      left = r.left + at(pos[0], r.width - w);
      top = r.top + at(pos.length > 1 ? pos[1] : pos[0], r.height - h);
    }
    var slack = 2;
    return x >= left - slack && x <= left + w + slack && y >= top - slack && y <= top + h + slack;
  }

  /** What a tap at (x, y) does, as the reader's set: -1 back, 1 on, 0 the controls (always the middle). */
  function tapZone(x, y) {
    // How far in from the edges a tap turns the page: the rest, the middle, is for the controls.
    var f = Math.max(0.05, Math.min(0.4, settings.tapWidth || 0.14));
    var side = x < W * f ? -1 : x > W * (1 - f) ? 1 : 0;
    switch (settings.taps) {
      case 'none': return 0;
      case 'forward': return side !== 0 ? 1 : 0;
      case 'topbottom':
        if (y < H * f) return -1;
        if (y > H * (1 - f)) return 1;
        return side;
      default: return side;
    }
  }

  document.addEventListener('keydown', function (e) {
    if (!ready) return;
    switch (e.key) {
      case 'ArrowRight': case 'PageDown': case ' ': case 'ArrowDown': next(); break;
      case 'ArrowLeft': case 'PageUp': case 'ArrowUp': prev(); break;
      default: return;
    }
    e.preventDefault();
  });

  var wheelAt = 0;
  document.addEventListener('wheel', function (e) {
    // A scrolled chapter scrolls as any page does.
    if (!ready || scrolled) return;
    e.preventDefault();
    var now = Date.now();
    if (now - wheelAt < 350) return;
    wheelAt = now;
    if ((e.deltaY || e.deltaX) > 0) next(); else prev();
  }, { passive: false });

  // Pages: nothing is scrolled by the browser itself. Scrolling: where the reader is, as it goes by.
  window.addEventListener('scroll', function () {
    if (!ready) return;
    if (scrolled) {
      if (scroller.scrollLeft) setScroll(0);
      if (auto.on && autoPos >= 0 && Math.abs(scroller.scrollTop - autoPos) > 2) {
        // Moved by a finger, or still gliding after one: autoscroll carries on once it has settled.
        autoWaitUntil = Math.max(autoWaitUntil, performance.now() + 200);
      }
      reportWhileScrolling();
      return;
    }
    if (animating || touch) return;
    var x = page * W;
    if (Math.abs(scroller.scrollLeft - x) > 1) setScroll(x);
  });

  // ---- how fast the words go by -------------------------------------------------------------------

  /** Words in some text. */
  function countWords(text) {
    var m = String(text || '').match(/\S+/g);
    return m ? m.length : 0;
  }

  // How tall a line is, and how far down the page a word takes on average (lines, and the space
  // between paragraphs), measured from the chapter's own paragraphs as they're laid out.
  var pxPerWord = 2.5;

  function measureDensity() {
    var body = document.body;
    if (!body) return;
    var ps = body.getElementsByTagName('p');
    var words = 0, height = 0, lhSum = 0, n = 0;
    for (var i = 0; i < ps.length && n < 40; i++) {
      var p = ps[i];
      var wc = countWords(p.textContent);
      if (wc < 20) continue;
      var rs = p.getClientRects(), h = 0;
      for (var k = 0; k < rs.length; k++) h += rs[k].height;
      if (h <= 0) continue;
      var cs = getComputedStyle(p);
      var lh = parseFloat(cs.lineHeight);
      if (!(lh > 0)) lh = (parseFloat(cs.fontSize) || 16) * 1.3;
      // The space to the next paragraph (margins between them fold into the larger one).
      h += Math.max(parseFloat(cs.marginTop) || 0, parseFloat(cs.marginBottom) || 0);
      words += wc;
      height += h;
      lhSum += lh;
      n++;
    }
    var fs = parseFloat(getComputedStyle(body).fontSize) || 16;
    lineH = n ? lhSum / n : fs * 1.4;
    if (n && words) pxPerWord = height / words;
    else {
      // Little ordinary text: about a word for every three letters' width of the line.
      var width = body.getBoundingClientRect().width || W;
      pxPerWord = lineH / Math.max(2, width / (fs * 2.9));
    }
  }

  /** About how many words are on the page showing (pages, not scrolling). */
  function wordsOnPage() {
    var left = page * W, right = left + W, words = 0;
    var nodes = textNodes();
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (!n.data.trim()) continue;
      var rs = rangeRects(n);
      if (!rs.length) continue;
      var inside = 0;
      for (var k = 0; k < rs.length; k++) {
        var x = rs[k].left + scroller.scrollLeft;
        if (x >= left - 1 && x < right) inside++;
      }
      if (inside) words += countWords(n.data) * inside / rs.length;
    }
    return words;
  }

  // ---- autoscroll ---------------------------------------------------------------------------------

  /**
   * Moving on by itself at a reading speed ([auto.wpm] words a minute): a scrolled chapter scrolls
   * steadily; pages turn when there's been time to read them. A finger on the page holds it, and it
   * carries on [auto.resume] seconds after (or stops, if that's below 0). At the end of a chapter it
   * waits [auto.endPause] seconds and goes on to the next (or stops, below 0). The app holds it while
   * something is shown over the page.
   */
  var auto = { on: false, wpm: 230, endPause: 3, resume: 2 };
  var autoHeld = false, autoTouching = false, autoWaitUntil = 0;
  var autoRaf = 0, autoLast = 0, autoPos = -1, autoEndSince = 0, autoAsked = false;
  var autoPageNeed = 0, autoPageDone = 0;
  /** The page autoscroll was stopped on (pages), so starting it again there carries on its time. */
  var autoStoppedOn = -1;

  function setAuto(a, fresh) {
    a = a || {};
    var was = auto.on;
    auto = {
      on: !!a.on,
      wpm: Math.max(40, Math.min(1500, +a.wpm || 230)),
      endPause: a.endPause == null ? 3 : +a.endPause,
      resume: a.resume == null ? 2 : +a.resume,
    };
    if (auto.on && !was) {
      // A chapter just opened by autoscroll shows its start for a moment before moving.
      autoWaitUntil = performance.now() + (fresh && scrolled ? Math.max(0, +a.startDelay || 0) : 0);
      autoPos = -1;
      autoEndSince = 0;
      autoAsked = false;
      // Started again on the page it was stopped on: the time already spent reading it counts.
      var before = autoPageDone, samePage = autoStoppedOn === page;
      startPageTimer();
      if (samePage) autoPageDone = Math.min(before, autoPageNeed);
    } else if (auto.on && !scrolled) {
      // A new speed: the page being read gets the time it needs at that speed.
      var done = autoPageDone;
      startPageTimer();
      autoPageDone = Math.min(done, autoPageNeed);
    }
    if (auto.on && !autoRaf) {
      autoLast = 0;
      autoRaf = requestAnimationFrame(autoTick);
    }
    if (!auto.on && was) autoStoppedOn = page;
    if (!auto.on && autoRaf) {
      cancelAnimationFrame(autoRaf);
      autoRaf = 0;
    }
    root.classList.toggle('kl-auto-pages', auto.on && !scrolled);
    showAutoBar(0);
  }

  /** Stopped here (a touch, the end of a chapter): the app is told, so its button shows it. */
  function stopAuto(reason) {
    if (!auto.on) return;
    autoStoppedOn = page;
    auto.on = false;
    if (autoRaf) { cancelAnimationFrame(autoRaf); autoRaf = 0; }
    root.classList.remove('kl-auto-pages');
    try { KL.auto(JSON.stringify({ on: false, reason: reason })); } catch (e) {}
  }

  function autoTouch(down) {
    if (down) { autoTouching = true; return; }
    autoTouching = false;
    if (!auto.on || keepsGoing()) return;
    if (auto.resume < 0) { stopAuto('touch'); return; }
    autoWaitUntil = performance.now() + auto.resume * 1000;
  }

  /** Set to keep going when the page is touched: a tap doesn't stop it, and a drag only while it lasts. */
  function keepsGoing() { return auto.resume === 0; }

  function autoTick(now) {
    autoRaf = requestAnimationFrame(autoTick);
    var dt = autoLast ? Math.min(80, now - autoLast) : 0;
    autoLast = now;
    if (!auto.on || !ready) return;
    var finger = keepsGoing() ? !!(touch && touch.moved) : autoTouching || !!touch;
    var holding = autoHeld || finger || now < autoWaitUntil || animating || selectionActive();
    if (scrolled) autoScrollTick(now, dt, holding); else autoPageTick(dt, holding);
  }

  /**
   * How far autoscroll goes in a scrolled chapter: until its last line has come up to about two fifths
   * of the way down the screen, where it has been read, rather than on through the space under it.
   */
  function autoEnd() {
    var textEnd = document.body.getBoundingClientRect().bottom + scroller.scrollTop;
    return Math.max(0, Math.min(maxScroll(), textEnd - bandTop - viewH() * 0.4));
  }

  function autoScrollTick(now, dt, holding) {
    if (holding) { autoPos = -1; autoEndSince = 0; return; }
    var max = autoEnd();
    // Moved by hand since: carry on from there.
    if (autoPos < 0 || Math.abs(autoPos - scroller.scrollTop) > 3) autoPos = scroller.scrollTop;
    if (autoPos >= max - 0.5) {
      if (!autoEndSince) autoEndSince = now;
      if (auto.endPause < 0) { stopAuto('chapter'); return; }
      if (!autoAsked && now - autoEndSince >= auto.endPause * 1000) {
        autoAsked = true;
        KL.edge(1);
      }
      return;
    }
    autoEndSince = 0;
    autoPos = Math.min(max, autoPos + auto.wpm / 60000 * pxPerWord * dt);
    scroller.scrollTop = autoPos;
  }

  function autoPageTick(dt, holding) {
    if (autoPageNeed <= 0) startPageTimer();
    if (holding) return;
    autoPageDone += dt;
    showAutoBar(autoPageDone / autoPageNeed);
    if (autoPageDone < autoPageNeed) return;
    if (page >= pages - 1) {
      if (auto.endPause < 0) { stopAuto('chapter'); return; }
      // On to the next chapter: nothing more here until it opens.
      autoPageNeed = 1e12;
      autoPageDone = 0;
      KL.edge(1);
      return;
    }
    autoPageNeed = 0;
    autoPageDone = 0;
    next(0.8);
  }

  /** The time the page showing needs at the reading speed (and the pause, at a chapter's last page). */
  function startPageTimer() {
    autoPageDone = 0;
    if (scrolled || !ready) { autoPageNeed = 0; return; }
    var words = wordsOnPage();
    var need = words < 5 ? 4000 : words / auto.wpm * 60000;
    need = Math.max(3000, need);
    if (page >= pages - 1 && auto.endPause > 0) need += auto.endPause * 1000;
    autoPageNeed = need;
  }

  function autoPageTurned() {
    if (!auto.on || scrolled) return;
    startPageTimer();
    showAutoBar(0);
  }

  /** A thin line along the foot of the page, under the progress bar, filling up until the page turns. */
  function showAutoBar(f) {
    var bar = document.getElementById('kl-autobar');
    if (!bar) {
      if (!auto.on || scrolled) return;
      bar = document.createElementNS(XHTML, 'div');
      bar.id = 'kl-autobar';
      root.appendChild(bar);
    }
    bar.style.transform = 'scaleX(' + Math.max(0, Math.min(1, f || 0)) + ')';
  }

  // ---- what the app can ask -----------------------------------------------------------------------

  /**
   * Settings that leave the text where it is: how pages turn, and the page's colours. Changing only
   * these needs no laying out again, which would move a scrolled chapter.
   */
  var NOT_LAYOUT = {
    turn: 1, animate: 1, taps: 1, tapWidth: 1, blend: 1, barColor: 1,
    bg: 1, fg: 1, link: 1, sel: 1, hl: 1, recolor: 1, dimImages: 1,
  };

  function sameLayout(a, b) {
    var keys = Object.keys(a).concat(Object.keys(b));
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (NOT_LAYOUT[k]) continue;
      if (JSON.stringify(a[k]) !== JSON.stringify(b[k])) return false;
    }
    return true;
  }

  window.KLR = {
    next: function () { next(); },
    prev: function () { prev(); },
    turnNow: turnNow,
    selection: function () { var s0 = selectionInfo(); return s0 ? JSON.stringify(s0) : ''; },
    /** The user's fonts changed (one was added): their list is read again, and the page laid out in them. */
    userFonts: function () {
      var own = document.querySelector('link[href*="/app/reader/reader.css"]');
      if (!own) return;
      var old = document.getElementById('kl-userfonts');
      var link = document.createElement('link');
      link.rel = 'stylesheet';
      link.type = 'text/css';
      link.href = own.href.replace(/reader\.css.*$/, 'userfonts.css?' + Date.now());
      link.onload = function () {
        if (old && old.parentNode) old.parentNode.removeChild(old);
        link.id = 'kl-userfonts';
        var again = function () { if (ready) relayout(function () {}); };
        if (document.fonts && document.fonts.load && settings && settings.font) document.fonts.load('16px ' + settings.font).then(again, again);
        else again();
      };
      document.head.appendChild(link);
    },
    clearSelection: function () { try { window.getSelection().removeAllRanges(); } catch (e) {} },
    setAnnotations: function (json) { applyAnnotations(parse(json).list || []); },
    annotationRect: function (id) { var a = annotationById(id); return a ? JSON.stringify(rectJson(a.range.getBoundingClientRect())) : ''; },
    go: function (json, animate) { goTo(parse(json), animate); },
    apply: function (json) {
      var s = parse(json);
      if (ready && sameLayout(settings, s)) {
        var blendChanged = settings.blend !== s.blend;
        settings = s;
        applyColours(s);
        if (blendChanged) blendPictures();
        return;
      }
      relayout(function () { applySettings(s); });
    },
    here: function () { return JSON.stringify(here()); },
    /** In the page the app counts pages in: counts the chapter at [url] as [json] says, as count [id]. */
    count: function (id, url, json) { countInShell(id, url, parse(json)); },
    clear: function () { clearHighlight('kl-search'); },
    /** Autoscroll: on or off, its speed and timings ({on, wpm, endPause, resume}). */
    auto: function (json) { setAuto(parse(json), false); },
    /** Something is over the page: autoscroll waits. */
    autoHold: function (on) { autoHeld = !!on; },
    info: function () {
      return JSON.stringify({
        page: page, pages: pages, columns: columns, W: W, H: H, padX: padX, padY: padY, padTop: padTop, padBottom: padBottom, scrollWidth: scroller.scrollWidth,
        scrolled: scrolled, scrollTop: scroller.scrollTop, scrollHeight: scroller.scrollHeight, maxScroll: maxScroll(), textMax: scrolled ? textMax() : 0,
        step: stepH(), lineH: lineH, pxPerWord: pxPerWord, auto: auto.on, wpm: auto.wpm, pageNeed: autoPageNeed, pageDone: autoPageDone,
      });
    },
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
