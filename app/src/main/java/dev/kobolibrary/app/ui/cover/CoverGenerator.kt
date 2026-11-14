package dev.kobolibrary.app.ui.cover

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import dev.kobolibrary.app.R
import dev.kobolibrary.app.epub.Names
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * The cover maker: draws a cover for a book that has none, from its title, series and authors.
 *
 * The six designs, the colour themes and the way the words are fitted are all this app's own.
 * The lettering is Lora and Poppins (SIL Open Font Licence), with Liberation Serif and Sans
 * standing in for the scripts those two don't have.
 */

/** The designs a cover can be drawn in. */
enum class CoverStyle(val label: String) {
    /** A cloth-bound board with a pasted paper label. */
    LABEL("Label"),

    /** A ruled frame with scooped corners, like an old title page. */
    CLASSIC("Classic"),

    /** A sun coming up over water. */
    SUNRISE("Sunrise"),

    /** Two colours cut on a slant. */
    SPLIT("Split"),

    /** A band of tiles that is different for every book. */
    MOSAIC("Mosaic"),

    /** Big plain lettering and one large shape. */
    BOLD("Bold"),
}

/**
 * A cover's colours: the [ground] it is painted on and the [ink] for words on it, and an [accent]
 * for shapes and panels with the [accentInk] for words on those.
 */
data class CoverColors(val name: String, val ground: Int, val accent: Int, val ink: Int, val accentInk: Int) {
    companion object {
        private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()

        /** The built-in themes: four dark boards, three light ones and one in plain black and white. */
        val themes = listOf(
            CoverColors("Midnight", rgb(0x1B2A49), rgb(0xE0B458), rgb(0xF4EBD6), rgb(0x16223B)),
            CoverColors("Claret", rgb(0x6B1D2A), rgb(0xE8CFA4), rgb(0xF8EEDC), rgb(0x4A1220)),
            CoverColors("Forest", rgb(0x1F4035), rgb(0xDCC98C), rgb(0xF2EDD8), rgb(0x15302A)),
            CoverColors("Slate", rgb(0x36414B), rgb(0xA9CBD0), rgb(0xEEF3F4), rgb(0x202A31)),
            CoverColors("Parchment", rgb(0xEFE3C6), rgb(0x9A3A2B), rgb(0x2E251B), rgb(0xFAF1DC)),
            CoverColors("Linen", rgb(0xE4E9EC), rgb(0x2F5D7C), rgb(0x1E2B36), rgb(0xF2F6F8)),
            CoverColors("Saffron", rgb(0xEDB84A), rgb(0x2A2622), rgb(0x2A2622), rgb(0xF6DFA6)),
            CoverColors("Ink", rgb(0xF5F4EF), rgb(0x1C1C1C), rgb(0x1C1C1C), rgb(0xF5F4EF)),
        )

        /**
         * A theme in any colour. [hue] is 0–360 and [strength] 0–1 (0 is grey). A [dark] theme is a
         * deep board with pale lettering; a light one is the other way about.
         */
        fun fromHue(hue: Float, strength: Float, dark: Boolean = true): CoverColors {
            val h = ((hue % 360f) + 360f) % 360f
            val s = strength.coerceIn(0f, 1f)
            fun hsl(turn: Float, sat: Float, light: Float) = ColorUtils.HSLToColor(floatArrayOf((h + turn) % 360f, (sat * s).coerceIn(0f, 1f), light))
            return if (dark) {
                // The accent leans a little towards the next colour round the wheel, so it isn't just a tint.
                CoverColors("Custom", ground = hsl(0f, 0.52f, 0.22f), accent = hsl(28f, 0.62f, 0.76f), ink = hsl(0f, 0.30f, 0.94f), accentInk = hsl(0f, 0.55f, 0.14f))
            } else {
                CoverColors("Custom", ground = hsl(0f, 0.46f, 0.90f), accent = hsl(0f, 0.62f, 0.30f), ink = hsl(0f, 0.50f, 0.13f), accentInk = hsl(0f, 0.30f, 0.95f))
            }
        }
    }
}

data class CoverDesign(val style: CoverStyle, val colors: CoverColors) {
    companion object {
        /** A design picked by chance: any style, in a built-in theme or (with [anyColour]) sometimes a colour of its own. */
        fun random(rnd: Random = Random.Default, anyColour: Boolean = true): CoverDesign {
            val style = CoverStyle.entries.random(rnd)
            val colors = if (anyColour && rnd.nextInt(3) == 0) {
                CoverColors.fromHue(rnd.nextFloat() * 360f, 0.45f + rnd.nextFloat() * 0.55f, dark = rnd.nextBoolean())
            } else {
                CoverColors.themes.random(rnd)
            }
            return CoverDesign(style, colors)
        }
    }
}

/** What goes on a generated cover. */
data class CoverText(val title: String, val authors: List<String>, val series: String?, val seriesIndex: Double?)

class CoverGenerator(context: Context) {

    private val res: Resources = context.resources
    private val serif = face(R.font.lora_variable, 400, false, R.font.liberation_serif_regular)
    private val serifBold = face(R.font.lora_variable, 700, false, R.font.liberation_serif_bold)
    private val serifItalic = face(R.font.lora_italic_variable, 400, true, R.font.liberation_serif_regular)
    private val sans = face(R.font.poppins_medium, 500, false, R.font.liberation_sans_regular)
    private val sansBold = face(R.font.poppins_bold, 700, false, R.font.liberation_sans_bold)

    /** One of the cover typefaces at a weight, falling back to [spare] for letters it doesn't have. */
    private fun face(font: Int, weight: Int, italic: Boolean, spare: Int): Typeface {
        val slant = if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT
        return try {
            val main = Font.Builder(res, font).setWeight(weight).setSlant(slant).setFontVariationSettings("'wght' $weight").build()
            val other = Font.Builder(res, spare).setWeight(weight).setSlant(slant).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(main).build())
                .addCustomFallback(FontFamily.Builder(other).build())
                .setStyle(FontStyle(weight, slant))
                .build()
        } catch (e: Exception) {
            Typeface.create(if (font == R.font.poppins_medium || font == R.font.poppins_bold) Typeface.SANS_SERIF else Typeface.SERIF, weight, italic)
        }
    }

    /** Draws the cover at [width] × [height] (the Kobo Clara's screen size by default). */
    fun render(text: CoverText, design: CoverDesign, width: Int = CoverCrop.OUT_WIDTH, height: Int = CoverCrop.OUT_HEIGHT): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sheet = Sheet(Canvas(bmp), width.toFloat(), height.toFloat(), design.colors)
        val title = text.title.trim().ifEmpty { "Untitled" }
        val author = authorLine(text)
        val words = Words(title, seriesLine(text), author, seed = "$title\u0000${author.orEmpty()}".hashCode())
        when (design.style) {
            CoverStyle.LABEL -> label(sheet, words)
            CoverStyle.CLASSIC -> classic(sheet, words)
            CoverStyle.SUNRISE -> sunrise(sheet, words)
            CoverStyle.SPLIT -> split(sheet, words)
            CoverStyle.MOSAIC -> mosaic(sheet, words)
            CoverStyle.BOLD -> bold(sheet, words)
        }
        return bmp
    }

    // ---- the words -----------------------------------------------------------------------------

    private class Words(val title: String, val series: String?, val author: String?, val seed: Int)

    /** "Sherlock Holmes · Book 5", or just the series when it has no number. */
    private fun seriesLine(t: CoverText): String? {
        val name = t.series?.trim().orEmpty()
        if (name.isEmpty()) return null
        val number = t.seriesIndex ?: return name
        return "$name · Book ${Names.formatIndex(number)}"
    }

    /** Everyone who wrote it on one line: "A", "A & B", "A, B & C", and past three, "A, B & others". */
    private fun authorLine(t: CoverText): String? {
        val names = t.authors.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
        return when (names.size) {
            0 -> null
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            3 -> "${names[0]}, ${names[1]} & ${names[2]}"
            else -> "${names[0]}, ${names[1]} & others"
        }
    }

    /**
     * [s] in capitals, for lines set in spaced caps. Greek capitals go without their accents and
     * breathings, as Greek is set in capitals (a diaeresis stays, and an iota subscript becomes Ι).
     */
    private fun caps(s: String): String {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var greek = false
        for (ch in decomposed) {
            val mark = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
            if (!mark) greek = Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.GREEK
            if (mark && greek && ch != '\u0308' && ch != '\u0345') continue
            out.append(ch)
        }
        return Normalizer.normalize(out, Normalizer.Form.NFC).uppercase(Locale.ROOT)
    }

    /** Wrapped text at the size it was fitted to. It is placed by the top of its capital letters. */
    private class Fitted(val layout: StaticLayout, private val capTop: Float) {
        /** From the top of the first line's capitals down to the last line's baseline. */
        val height: Float get() = layout.getLineBaseline(layout.lineCount - 1) - capTop

        fun draw(canvas: Canvas, x: Float, y: Float, colour: Int) {
            canvas.save()
            canvas.translate(x, y - capTop)
            layout.paint.color = colour
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Sets [text] in a box [width] wide: at [largest] if it fits in [height] without a word being
     * broken in two, otherwise at the biggest size down to [smallest] that does. Text that is too
     * long even then is cut short with an ellipsis. [pitch] is the distance between lines and
     * [tracking] the extra space between letters, both in ems.
     */
    private fun fit(
        text: String?, face: Typeface, largest: Float, smallest: Float, width: Float, height: Float,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, pitch: Float = 1.18f, tracking: Float = 0f,
    ): Fitted? {
        if (text.isNullOrBlank()) return null
        val box = width.toInt().coerceAtLeast(1)
        val capital = Rect()

        fun set(size: Float, lines: Int = Int.MAX_VALUE): Fitted {
            // A paint of its own each time: the layout keeps hold of it.
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = face
                letterSpacing = tracking
                textSize = size
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, box)
                .setAlignment(align)
                .setLineSpacing(0f, pitch * size / paint.fontSpacing)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(lines)
                .setEllipsize(if (lines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
                .build()
            paint.getTextBounds("H", 0, 1, capital)
            return Fitted(layout, (layout.getLineBaseline(0) - capital.height()).toFloat())
        }

        var size = largest
        while (size > smallest) {
            val tried = set(size)
            if (tried.height <= height && !splitsAWord(tried.layout)) return tried
            size *= 0.94f
        }
        // Even the smallest size is too much: keep the lines there is room for.
        val capHeight = set(smallest, 1).height
        val lines = (1 + ((height - capHeight) / (pitch * smallest)).toInt()).coerceAtLeast(1)
        return set(smallest, lines)
    }

    /** Whether a line ends in the middle of a word (in a script that puts spaces between its words). */
    private fun splitsAWord(layout: StaticLayout): Boolean {
        val text = layout.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            if (before.isLetterOrDigit() && after.isLetterOrDigit() && !wrapsAnywhere(before) && !wrapsAnywhere(after)) return true
        }
        return false
    }

    private fun wrapsAnywhere(c: Char): Boolean {
        if (Character.isIdeographic(c.code)) return true
        return when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR -> true
            else -> false
        }
    }

    // ---- drawing -------------------------------------------------------------------------------

    /** The page being drawn. [u] is a hundredth of its width, the unit every measurement is in. */
    private class Sheet(val canvas: Canvas, val w: Float, val h: Float, val c: CoverColors) {
        val u = w / 100f

        fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.FILL }

        fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            style = Paint.Style.STROKE
            strokeWidth = width
        }

        fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int) = canvas.drawRect(left, top, right, bottom, fill(colour))

        /** A horizontal rule [length] long, centred on [cx]. */
        fun rule(cx: Float, y: Float, length: Float, thickness: Float, colour: Int) =
            rect(cx - length / 2, y - thickness / 2, cx + length / 2, y + thickness / 2, colour)

        /** A lozenge centred on ([cx], [cy]). */
        fun diamond(cx: Float, cy: Float, half: Float, colour: Int) {
            val p = Path().apply {
                moveTo(cx, cy - half)
                lineTo(cx + half, cy)
                lineTo(cx, cy + half)
                lineTo(cx - half, cy)
                close()
            }
            canvas.drawPath(p, fill(colour))
        }
    }

    private fun mix(from: Int, to: Int, amount: Float) = ColorUtils.blendARGB(from, to, amount)
    private fun faint(colour: Int, alpha: Int) = ColorUtils.setAlphaComponent(colour, alpha)

    /** A cloth-bound board with a hinge down the left and a paper label for the title. */
    private fun label(s: Sheet, t: Words) {
        val u = s.u
        s.canvas.drawColor(s.c.ground)
        // The cloth catches the light at the head and falls into shade at the foot (less so on a pale cloth, which only looks dirty for it).
        val pale = ColorUtils.calculateLuminance(s.c.ground) > 0.4
        val sheen = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, s.h, faint(Color.WHITE, if (pale) 40 else 22), faint(Color.BLACK, if (pale) 18 else 48), Shader.TileMode.CLAMP)
        }
        s.canvas.drawRect(0f, 0f, s.w, s.h, sheen)
        val hinge = 7f * u
        s.rect(0f, 0f, hinge, s.h, faint(Color.BLACK, if (pale) 20 else 36))
        s.rect(hinge, 0f, hinge + 0.4f * u, s.h, faint(Color.BLACK, if (pale) 46 else 72))
        s.rect(hinge + 0.4f * u, 0f, hinge + 0.8f * u, s.h, faint(Color.WHITE, 26))

        val cx = (hinge + s.w) / 2
        val paper = 66f * u
        val pad = 7f * u
        val inner = paper - 2 * pad
        val title = fit(t.title, serifBold, 11f * u, 4.6f * u, inner, 44f * u)!!
        val series = fit(t.series, serifItalic, 4.3f * u, 3f * u, inner, 11f * u)
        val gap = 3.6f * u
        val tall = title.height + if (series != null) gap + 0.3f * u + gap + series.height else 0f
        val top = 19f * u
        val sticker = RectF(cx - paper / 2, top, cx + paper / 2, top + tall + 2 * pad)
        val pasted = s.fill(s.c.accent).apply { setShadowLayer(1.6f * u, 0f, 0.7f * u, faint(Color.BLACK, 90)) }
        s.canvas.drawRect(sticker, pasted)
        val edge = 1.6f * u
        s.canvas.drawRect(sticker.left + edge, sticker.top + edge, sticker.right - edge, sticker.bottom - edge, s.stroke(faint(s.c.accentInk, 150), 0.3f * u))
        title.draw(s.canvas, sticker.left + pad, sticker.top + pad, s.c.accentInk)
        if (series != null) {
            val y = sticker.top + pad + title.height + gap
            s.rule(cx, y, 14f * u, 0.3f * u, faint(s.c.accentInk, 150))
            series.draw(s.canvas, sticker.left + pad, y + gap, s.c.accentInk)
        }

        val author = fit(t.author?.let(::caps), sans, 3.7f * u, 2.4f * u, 74f * u, 15f * u, tracking = 0.12f, pitch = 1.5f) ?: return
        val y = s.h - 11f * u - author.height
        s.rule(cx, y - 4.5f * u, 10f * u, 0.3f * u, faint(s.c.ink, 140))
        author.draw(s.canvas, cx - 37f * u, y, s.c.ink)
    }

    /** Two ruled frames, the inner one with scooped corners, and a lozenge under the title. */
    private fun classic(s: Sheet, t: Words) {
        val u = s.u
        s.canvas.drawColor(s.c.ground)
        val out = 4.5f * u
        s.canvas.drawRect(out, out, s.w - out, s.h - out, s.stroke(s.c.accent, 0.9f * u))
        val l = 7.4f * u
        val r = s.w - l
        val top = l
        val bottom = s.h - l
        val scoop = 3.4f * u
        val frame = Path().apply {
            moveTo(l + scoop, top)
            lineTo(r - scoop, top)
            arcTo(RectF(r - scoop, top - scoop, r + scoop, top + scoop), 180f, -90f)
            lineTo(r, bottom - scoop)
            arcTo(RectF(r - scoop, bottom - scoop, r + scoop, bottom + scoop), 270f, -90f)
            lineTo(l + scoop, bottom)
            arcTo(RectF(l - scoop, bottom - scoop, l + scoop, bottom + scoop), 0f, -90f)
            lineTo(l, top + scoop)
            arcTo(RectF(l - scoop, top - scoop, l + scoop, top + scoop), 90f, -90f)
            close()
        }
        s.canvas.drawPath(frame, s.stroke(s.c.accent, 0.3f * u))
        for (x in floatArrayOf(l, r)) for (y in floatArrayOf(top, bottom)) s.canvas.drawCircle(x, y, 0.75f * u, s.fill(s.c.accent))

        val cx = s.w / 2
        val wide = 68f * u
        val title = fit(t.title, serifBold, 11f * u, 4.8f * u, wide, 42f * u)!!
        var y = 20f * u
        title.draw(s.canvas, cx - wide / 2, y, s.c.ink)
        y += title.height + 6.5f * u
        s.diamond(cx, y, 1.3f * u, s.c.accent)
        s.rule(cx - 10f * u, y, 13f * u, 0.3f * u, s.c.accent)
        s.rule(cx + 10f * u, y, 13f * u, 0.3f * u, s.c.accent)
        fit(t.series, serifItalic, 4.6f * u, 3f * u, wide, 11f * u)?.draw(s.canvas, cx - wide / 2, y + 5.5f * u, s.c.accent)

        val author = fit(t.author?.let(::caps), serif, 4f * u, 2.6f * u, wide, 15f * u, tracking = 0.14f, pitch = 1.45f) ?: return
        author.draw(s.canvas, cx - wide / 2, s.h - 17f * u - author.height, s.c.ink)
    }

    /** A ringed sun coming up over the water, with the author's name below the horizon. */
    private fun sunrise(s: Sheet, t: Words) {
        val u = s.u
        val horizon = s.h - 35f * u
        val cx = s.w / 2
        s.canvas.drawColor(s.c.ground)
        s.canvas.save()
        s.canvas.clipRect(0f, 0f, s.w, horizon)
        val rings = listOf(36f to 0.14f, 29f to 0.32f, 22f to 0.55f, 15f to 0.84f)
        for ((radius, glow) in rings) s.canvas.drawCircle(cx, horizon, radius * u, s.fill(mix(s.c.ground, s.c.accent, glow)))
        s.canvas.restore()
        s.rect(0f, horizon, s.w, s.h, s.c.accent)
        // The sun's light lying on the water.
        val glint = s.stroke(mix(s.c.accent, s.c.ground, 0.32f), 0.9f * u).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0..2) {
            val half = (15f - 4.5f * i) * u
            val y = horizon + (3.4f + 3.3f * i) * u
            s.canvas.drawLine(cx - half, y, cx + half, y, glint)
        }

        // The words sit in the middle of the sky that is left above the sun.
        val wide = 82f * u
        val head = 9f * u
        val sky = horizon - 36f * u - 7f * u - head
        val series = fit(t.series?.let(::caps), sans, 3f * u, 2.2f * u, wide, 8f * u, tracking = 0.14f, pitch = 1.5f)
        val above = if (series != null) series.height + 4.4f * u else 0f
        val title = fit(t.title, sansBold, 11f * u, 4.2f * u, wide, sky - above, pitch = 1.2f)!!
        var y = head + (sky - above - title.height) / 2
        series?.let {
            it.draw(s.canvas, cx - wide / 2, y, s.c.accent)
            y += above
        }
        title.draw(s.canvas, cx - wide / 2, y, s.c.ink)

        val author = fit(t.author, sans, 4.2f * u, 2.6f * u, 80f * u, 14f * u, pitch = 1.35f) ?: return
        val middle = (horizon + 13f * u + s.h - 6f * u) / 2
        author.draw(s.canvas, cx - 40f * u, middle - author.height / 2, s.c.accentInk)
    }

    /** The ground above and the accent below, cut on a slant, with a pinstripe along the cut. */
    private fun split(s: Sheet, t: Words) {
        val u = s.u
        s.canvas.drawColor(s.c.ground)
        val left = s.h - 40f * u
        val right = s.h - 66f * u
        fun slab(lift: Float, depth: Float?) = Path().apply {
            moveTo(0f, left - lift)
            lineTo(s.w, right - lift)
            if (depth == null) {
                lineTo(s.w, s.h)
                lineTo(0f, s.h)
            } else {
                lineTo(s.w, right - lift + depth)
                lineTo(0f, left - lift + depth)
            }
            close()
        }
        s.canvas.drawPath(slab(0f, null), s.fill(s.c.accent))
        s.canvas.drawPath(slab(3.6f * u, 1.1f * u), s.fill(s.c.accent))

        val x = 9f * u
        val wide = 82f * u
        var y = 11f * u
        fit(t.series?.let(::caps), sans, 3.1f * u, 2.2f * u, wide, 8f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.5f)?.let {
            it.draw(s.canvas, x, y, s.c.accent)
            y += it.height + 4.2f * u
        }
        fit(t.title, sansBold, 12.5f * u, 4.6f * u, wide, right - 9f * u - y, Layout.Alignment.ALIGN_NORMAL, pitch = 1.16f)!!.draw(s.canvas, x, y, s.c.ink)

        val author = fit(t.author, sans, 4.4f * u, 2.6f * u, 70f * u, 17f * u, Layout.Alignment.ALIGN_OPPOSITE, pitch = 1.35f) ?: return
        author.draw(s.canvas, s.w - x - 70f * u, s.h - 10f * u - author.height, s.c.accentInk)
    }

    /** A band of tiles, laid by chance from the book's own title, so no two books match. */
    private fun mosaic(s: Sheet, t: Words) {
        val u = s.u
        s.canvas.drawColor(s.c.ground)
        val across = 6
        val down = 4
        val tile = s.w / across
        val foot = 19f * u
        val bandTop = s.h - foot - down * tile
        val shades = listOf(0.22f, 0.48f, 0.74f, 1f).map { mix(s.c.ground, s.c.accent, it) }
        val rnd = Random(t.seed)
        val flat = Paint().apply { style = Paint.Style.FILL }
        for (row in 0 until down) for (col in 0 until across) {
            // Whole pixels, so neighbouring tiles meet without a seam.
            val x0 = (col * tile).roundToInt().toFloat()
            val x1 = ((col + 1) * tile).roundToInt().toFloat()
            val y0 = (bandTop + row * tile).roundToInt().toFloat()
            val y1 = (bandTop + (row + 1) * tile).roundToInt().toFloat()
            val under = rnd.nextInt(shades.size)
            val over = (under + 1 + rnd.nextInt(shades.size - 1)) % shades.size
            flat.color = shades[under]
            s.canvas.drawRect(x0, y0, x1, y1, flat)
            val shape = s.fill(shades[over])
            s.canvas.save()
            s.canvas.clipRect(x0, y0, x1, y1)
            when (rnd.nextInt(7)) {
                0 -> s.canvas.drawCircle(x0, y0, x1 - x0, shape)
                1 -> s.canvas.drawCircle(x1, y0, x1 - x0, shape)
                2 -> s.canvas.drawCircle(x1, y1, x1 - x0, shape)
                3 -> s.canvas.drawCircle(x0, y1, x1 - x0, shape)
                4 -> s.canvas.drawPath(Path().apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x0, y1); close() }, shape)
                5 -> s.canvas.drawPath(Path().apply { moveTo(x1, y0); lineTo(x1, y1); lineTo(x0, y1); close() }, shape)
                else -> s.canvas.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) * 0.3f, shape)
            }
            s.canvas.restore()
        }

        val cx = s.w / 2
        val wide = 82f * u
        val series = fit(t.series, serifItalic, 4.4f * u, 3f * u, wide, 6f * u)
        val below = if (series != null) 4.6f * u + series.height else 0f
        val title = fit(t.title, serifBold, 10f * u, 4.4f * u, wide, bandTop - 19f * u - below)!!
        var y = (bandTop - title.height - below) / 2
        title.draw(s.canvas, cx - wide / 2, y, s.c.ink)
        y += title.height + 4.6f * u
        series?.draw(s.canvas, cx - wide / 2, y, s.c.accent)

        val author = fit(t.author?.let(::caps), sans, 3.7f * u, 2.4f * u, wide, 11f * u, tracking = 0.12f, pitch = 1.5f) ?: return
        author.draw(s.canvas, cx - wide / 2, s.h - foot / 2 - author.height / 2, s.c.ink)
    }

    /** Big plain lettering set to the left, a bar above it and a quarter-disc in the corner. */
    private fun bold(s: Sheet, t: Words) {
        val u = s.u
        s.canvas.drawColor(s.c.ground)
        s.canvas.drawCircle(s.w, s.h, 34f * u, s.fill(s.c.accent))
        s.canvas.drawCircle(s.w, s.h, 41f * u, s.stroke(s.c.accent, 0.8f * u))
        val x = 9f * u
        s.rect(x, 9f * u, x + 18f * u, 11.2f * u, s.c.accent)

        val wide = 82f * u
        var y = 17f * u
        fit(t.series?.let(::caps), sans, 3.1f * u, 2.2f * u, wide, 8f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.5f)?.let {
            it.draw(s.canvas, x, y, s.c.accent)
            y += it.height + 4.4f * u
        }
        fit(t.title, sansBold, 14.5f * u, 5f * u, wide, s.h - 50f * u - y, Layout.Alignment.ALIGN_NORMAL, pitch = 1.1f)!!.draw(s.canvas, x, y, s.c.ink)

        val author = fit(t.author, sans, 4.2f * u, 2.6f * u, 47f * u, 16f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 1.35f) ?: return
        author.draw(s.canvas, x, s.h - 10f * u - author.height, s.c.ink)
    }
}
