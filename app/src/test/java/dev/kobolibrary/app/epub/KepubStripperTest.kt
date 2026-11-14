package dev.kobolibrary.app.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KepubStripperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Shaped like kepubify's output. */
    private val kepub = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>Chapter 1</title>
<link href="../style.css" rel="stylesheet" type="text/css"/>
<style type="text/css" class="kobostylehacks">div#book-inner { margin-top: 0; margin-bottom: 0;}</style>
</head>
<body><div id="book-columns"><div id="book-inner">
<h1 class="chapter"><span class="koboSpan" id="kobo.1.1">Chapter One</span></h1>
<p><span class="koboSpan" id="kobo.2.1">It was a dark night. </span><span class="koboSpan" id="kobo.2.2">The <em>wind</em> howled &amp; the rain fell.</span></p>
<p class="x"><span class="smallcaps koboSpan" id="kobo.3.1">Kept span</span> <span class="note">real span</span></p>
<div class="scene"><span class="koboSpan" id="kobo.4.1">* * *</span></div>
<!-- <span class="koboSpan">inside a comment</span> -->
</div></div></body>
</html>"""

    private val expected = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>Chapter 1</title>
<link href="../style.css" rel="stylesheet" type="text/css"/>
</head>
<body>
<h1 class="chapter">Chapter One</h1>
<p>It was a dark night. The <em>wind</em> howled &amp; the rain fell.</p>
<p class="x"><span class="smallcaps">Kept span</span> <span class="note">real span</span></p>
<div class="scene">* * *</div>
<!-- <span class="koboSpan">inside a comment</span> -->
</body>
</html>"""

    @Test
    fun stripsKoboAdditionsOnly() {
        assertEquals(expected, KepubStripper.strip(kepub))
    }

    @Test
    fun leavesPlainContentAlone() {
        val plain = "<html><body><div id=\"main\"><p><span class=\"x\">Hi</span></p></div></body></html>"
        assertEquals(plain, KepubStripper.strip(plain))
        assertFalse(KepubStripper.looksLikeKepub(plain))
    }

    @Test
    fun stripsAWholeBook() {
        val file = TestEpubs.calibreEpub2(tmp.newFile("book.kepub.epub"), extra = mapOf("OEBPS/text/part0000.html" to kepub.toByteArray()))
        assertTrue(EpubEditor.stripKepub(file))
        EpubFile.open(file).use { assertEquals(expected, it.readText("OEBPS/text/part0000.html")) }
        assertFalse(EpubEditor.stripKepub(file)) // nothing left to do
    }
}
