package dev.kobolibrary.app.reader

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import dev.kobolibrary.app.epub.BookFiles
import dev.kobolibrary.app.epub.EpubFile
import dev.kobolibrary.app.epub.HtmlEntities
import dev.kobolibrary.app.epub.Xml
import dev.kobolibrary.app.epub.XmlCheck
import dev.kobolibrary.app.epub.XmlException
import java.io.ByteArrayInputStream
import java.net.URLDecoder

/**
 * Hands a book's files to a WebView, from a made-up web address that never reaches the network.
 * Chapters get the reader's (or editor's) style sheet and script added; nothing else is changed.
 */
class BookServer(private val assets: AssetManager, @Volatile var files: BookFiles) {

    /** What's added to every chapter's head. */
    @Volatile
    var inject: String = READER_HEAD

    /** Changes a chapter's text before it's served (the reader puts a chapter's opening page in front of it); null leaves it. */
    @Volatile
    var transform: ((path: String, text: String) -> String?)? = null

    /** Scripts only from the app: a book's own scripts don't run. */
    var scriptPolicy: String = "script-src $APP; object-src 'none'"

    fun urlFor(path: String, fragment: String? = null): String =
        BOOK + path.split('/').joinToString("/") { encode(it) } + (fragment?.let { "#" + encode(it) } ?: "")

    fun respond(url: String): WebResourceResponse? {
        val clean = url.substringBefore('#').substringBefore('?')
        return try {
            when {
                clean.startsWith(BOOK) -> book(decode(clean.removePrefix(BOOK)))
                clean.startsWith(APP) -> asset(clean.removePrefix(APP))
                // Nothing in a book goes to the internet from here.
                else -> notFound()
            }
        } catch (e: Exception) {
            // The book may have been closed while a page was still asking for it.
            notFound()
        }
    }

    private fun book(path: String): WebResourceResponse {
        val bytes = files.bytes(path) ?: return notFound()
        val type = mediaType(path)
        if (type == "application/xhtml+xml" || type == "text/html") {
            val decoded = EpubFile.decodeText(bytes)
            val source = transform?.let { t -> runCatching { t(path, decoded) }.getOrNull() } ?: decoded
            val (mime, text) = prepare(source, inject)
            return WebResourceResponse(mime, "utf-8", 200, "OK", headers(mime) + ("Content-Security-Policy" to scriptPolicy), text.toByteArray(Charsets.UTF_8).inputStream())
        }
        return WebResourceResponse(type, null, 200, "OK", headers(type), ByteArrayInputStream(bytes))
    }

    private fun asset(path: String): WebResourceResponse = try {
        val bytes = assets.open(path).use { it.readBytes() }
        val type = mediaType(path)
        WebResourceResponse(type, if (type.startsWith("text/") || type.endsWith("javascript")) "utf-8" else null, 200, "OK", headers(type), ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        notFound()
    }

    private fun headers(type: String) = mapOf("Cache-Control" to "no-store", "Content-Type" to type)

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    companion object {
        const val ORIGIN = "https://kobolibrary.invalid"
        const val BOOK = "$ORIGIN/book/"
        const val APP = "$ORIGIN/app/"
        const val XHTML_NS = "http://www.w3.org/1999/xhtml"

        const val READER_HEAD =
            "<meta name=\"viewport\" content=\"width=device-width, height=device-height, initial-scale=1, maximum-scale=1, user-scalable=no\"/>" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"${APP}reader/reader.css\"/>" +
                "<script type=\"text/javascript\" src=\"${APP}reader/reader.js\"></script>"

        fun encode(s: String): String = buildString {
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c.toChar() in "-._~!$&'()*+,;=:@") {
                    append(c.toChar())
                } else append('%').append("%02X".format(c))
            }
        }

        fun decode(s: String): String = try {
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            s
        }

        /** The book path a reader address points at, with its fragment. */
        fun pathOf(url: String): Pair<String, String?>? {
            if (!url.startsWith(BOOK)) return null
            val path = decode(url.removePrefix(BOOK).substringBefore('#').substringBefore('?'))
            val fragment = url.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { decode(it) }
            return path to fragment
        }

        fun mediaType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "js" -> "application/javascript"
            "woff2" -> "font/woff2"
            "woff" -> "font/woff"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "ncx" -> "application/x-dtbncx+xml"
            "xml", "opf" -> "application/xml"
            "svg" -> "image/svg+xml"
            "mp3" -> "audio/mpeg"
            "mp4", "m4v" -> "video/mp4"
            else -> EpubFile.mediaTypeFor(path)
        }

        /**
         * A chapter ready for a WebView: well-formed XHTML is served as XHTML (as e-readers do), with
         * HTML's named characters written as numbers so the XML reader knows them; anything else is
         * served as HTML in standards mode. [inject] goes at the end of the head.
         */
        fun prepare(source: String, inject: String): Pair<String, String> {
            // Only XHTML proper: elements outside the XHTML namespace aren't HTML to an XML reader.
            val strict = XmlCheck.check(source, htmlEntities = true) == null &&
                runCatching { Xml.parse(source).root.namespaceUri() == XHTML_NS }.getOrDefault(false)
            var text = if (strict) HtmlEntities.toNumeric(source) else source
            if (!strict) {
                // Without a doctype a browser lays the page out in "quirks mode".
                text = text.replaceFirst(Regex("^\\uFEFF?\\s*<\\?xml[^>]*\\?>\\s*"), "")
                    .replaceFirst(Regex("^\\s*<!DOCTYPE[^>\\[]*(\\[[^\\]]*\\])?\\s*>\\s*", RegexOption.IGNORE_CASE), "")
                text = "<!DOCTYPE html>\n$text"
            }
            text = injectHead(text, inject)
            return (if (strict) "application/xhtml+xml" else "text/html") to text
        }

        fun injectHead(text: String, inject: String): String {
            val doc = try {
                Xml.parse(text)
            } catch (e: XmlException) {
                null
            }
            val head = doc?.root?.descendants()?.firstOrNull { it.localName.equals("head", true) }
            return when {
                head != null && !head.selfClosing -> text.substring(0, head.endTagStart) + inject + text.substring(head.endTagStart)
                head != null -> text.substring(0, head.start) + "<head>" + inject + "</head>" + text.substring(head.end)
                doc != null && doc.root.localName.equals("html", true) ->
                    text.substring(0, doc.root.startTagEnd) + "<head>" + inject + "</head>" + text.substring(doc.root.startTagEnd)
                else -> inject + text
            }
        }
    }
}
