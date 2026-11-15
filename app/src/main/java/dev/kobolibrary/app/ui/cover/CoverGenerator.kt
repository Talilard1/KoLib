package dev.kobolibrary.app.ui.cover

import android.annotation.SuppressLint
import dev.kobolibrary.app.tr
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
 * The designs, the colour themes, the pictures (see CoverArt) and the way the words are fitted are all
 * this app's own. The lettering is Lora, Poppins, Cinzel, Cinzel Decorative, UnifrakturMaguntia, Great
 * Vibes, Bebas Neue, Orbitron, Poiret One and Fredoka (SIL Open Font Licence), with Liberation Serif
 * and Sans standing in for the scripts those don't have.
 */

/**
 * The designs a cover can be drawn in: the genres each suits best, whether it has a picture that can be
 * swapped for one of the reader's own ([picture]), and whether it's painted in the chosen colours
 * ([themed]) rather than its picture's.
 */
enum class CoverStyle(val label: String, val suits: Set<CoverGenre> = emptySet(), val picture: Boolean = false, val themed: Boolean = true) {
    /** A cloth-bound board with a pasted paper label. */
    LABEL(tr("Label"), setOf(CoverGenre.LITERARY, CoverGenre.NON_FICTION, CoverGenre.HISTORICAL)),

    /** A ruled frame with scooped corners, like an old title page. */
    CLASSIC(tr("Classic"), setOf(CoverGenre.LITERARY, CoverGenre.HISTORICAL, CoverGenre.MYSTERY, CoverGenre.ROMANCE)),

    /** A sun coming up over water. */
    SUNRISE(tr("Sunrise"), setOf(CoverGenre.LITERARY, CoverGenre.ROMANCE, CoverGenre.ADVENTURE)),

    /** Two colours cut on a slant. */
    SPLIT(tr("Split"), setOf(CoverGenre.THRILLER, CoverGenre.NON_FICTION)),

    /** A band of tiles that is different for every book. */
    MOSAIC(tr("Mosaic"), setOf(CoverGenre.NON_FICTION, CoverGenre.LITERARY)),

    /** Big plain lettering and one large shape. */
    BOLD(tr("Bold"), setOf(CoverGenre.THRILLER, CoverGenre.NON_FICTION)),

    /**
     * A dark board in a ruled frame: the author's name large at the head, a framed picture, then the title
     * and which book of its series it is, as long-running fantasy series are often reissued.
     */
    FRAMED(
        tr("Framed picture"),
        setOf(CoverGenre.FANTASY, CoverGenre.SCIENCE_FICTION, CoverGenre.HISTORICAL, CoverGenre.ADVENTURE, CoverGenre.WESTERN, CoverGenre.HORROR, CoverGenre.MYSTERY, CoverGenre.LITERARY),
        picture = true,
    ),

    /** Cloth-bound, with a line drawing and the lettering stamped in gold. */
    GILDED(tr("Gilded cloth"), setOf(CoverGenre.FANTASY, CoverGenre.LITERARY, CoverGenre.HISTORICAL, CoverGenre.ROMANCE, CoverGenre.CHILDREN, CoverGenre.ADVENTURE)),

    /** A painting over the whole cover, with the lettering over its sky. */
    PAINTING(
        tr("Painting"),
        setOf(CoverGenre.FANTASY, CoverGenre.SCIENCE_FICTION, CoverGenre.ADVENTURE, CoverGenre.WESTERN, CoverGenre.HISTORICAL, CoverGenre.THRILLER, CoverGenre.LITERARY, CoverGenre.ROMANCE),
        picture = true,
        themed = false,
    ),

    /** Black and white with one colour: light through blinds and a man in a hat. */
    NOIR(tr("Noir"), setOf(CoverGenre.MYSTERY, CoverGenre.THRILLER)),

    /** A pointed window on the night, with a house on a hill, and black-letter lettering. */
    GOTHIC(tr("Gothic"), setOf(CoverGenre.HORROR)),

    /** Stars, a glowing nebula and the curve of a planet. */
    STARFIELD(tr("Starfield"), setOf(CoverGenre.SCIENCE_FICTION)),

    /** Gold sunbursts and stepped frames, as in the 1920s. */
    DECO(tr("Deco"), setOf(CoverGenre.HISTORICAL, CoverGenre.MYSTERY, CoverGenre.ROMANCE, CoverGenre.LITERARY)),

    /** Blossoming branches and a flowing script. */
    BLOSSOM(tr("Blossom"), setOf(CoverGenre.ROMANCE)),

    /** Green hills, a sun and a balloon, and round friendly lettering. */
    STORYBOOK(tr("Storybook"), setOf(CoverGenre.CHILDREN), themed = false),

    /** A block of colour with rings and arcs, and plain modern lettering. */
    ESSAY(tr("Essay"), setOf(CoverGenre.NON_FICTION)),
    ;

    companion object {
        /** The designs whose picture or drawing is different each time it's drawn (see [CoverDesign.seed]). */
        val drawn: Set<CoverStyle> = setOf(FRAMED, GILDED, PAINTING, GOTHIC, STARFIELD, BLOSSOM, STORYBOOK)

        /** The designs that suit [genre] best, then the rest. */
        fun forGenre(genre: CoverGenre): List<CoverStyle> = entries.filter { genre in it.suits } + entries.filter { genre !in it.suits }
    }
}

/**
 * A cover's colours: the [ground] it is painted on and the [ink] for words on it, and an [accent]
 * for shapes and panels with the [accentInk] for words on those.
 */
data class CoverColors(val name: String, val ground: Int, val accent: Int, val ink: Int, val accentInk: Int) {
    companion object {
        private fun rgb(hex: Long) = (0xFF000000 or hex).toInt()

        /** The built-in themes: dark boards, light ones and one in plain black and white. */
        val themes = listOf(
            CoverColors(tr("Midnight"), rgb(0x1B2A49), rgb(0xE0B458), rgb(0xF4EBD6), rgb(0x16223B)),
            CoverColors(tr("Claret"), rgb(0x6B1D2A), rgb(0xE8CFA4), rgb(0xF8EEDC), rgb(0x4A1220)),
            CoverColors(tr("Forest"), rgb(0x1F4035), rgb(0xDCC98C), rgb(0xF2EDD8), rgb(0x15302A)),
            CoverColors(tr("Slate"), rgb(0x36414B), rgb(0xA9CBD0), rgb(0xEEF3F4), rgb(0x202A31)),
            CoverColors(tr("Charcoal"), rgb(0x2A2B2F), rgb(0xD8B562), rgb(0xF1E7D0), rgb(0x1C1D20)),
            CoverColors(tr("Umber"), rgb(0x3E2B20), rgb(0xE2BE74), rgb(0xF5E9D2), rgb(0x2C1E16)),
            CoverColors(tr("Plum"), rgb(0x3A2341), rgb(0xE6C27E), rgb(0xF4E9F0), rgb(0x2A1830)),
            CoverColors(tr("Teal"), rgb(0x163C42), rgb(0xE0C27C), rgb(0xEAF2EE), rgb(0x0F2B2F)),
            CoverColors(tr("Parchment"), rgb(0xEFE3C6), rgb(0x9A3A2B), rgb(0x2E251B), rgb(0xFAF1DC)),
            CoverColors(tr("Linen"), rgb(0xE4E9EC), rgb(0x2F5D7C), rgb(0x1E2B36), rgb(0xF2F6F8)),
            CoverColors(tr("Blush"), rgb(0xF6E5E2), rgb(0xA8435E), rgb(0x4A2230), rgb(0xFBEFEC)),
            CoverColors(tr("Saffron"), rgb(0xEDB84A), rgb(0x2A2622), rgb(0x2A2622), rgb(0xF6DFA6)),
            CoverColors(tr("Ink"), rgb(0xF5F4EF), rgb(0x1C1C1C), rgb(0x1C1C1C), rgb(0xF5F4EF)),
        )

        /** The themes with a dark board (pale lettering). */
        val dark: List<CoverColors> get() = themes.filter { ColorUtils.calculateLuminance(it.ground) < 0.3 }

        /** The themes with a pale board (dark lettering). */
        val light: List<CoverColors> get() = themes.filter { ColorUtils.calculateLuminance(it.ground) >= 0.3 }

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
                CoverColors(tr("Custom"), ground = hsl(0f, 0.52f, 0.22f), accent = hsl(28f, 0.62f, 0.76f), ink = hsl(0f, 0.30f, 0.94f), accentInk = hsl(0f, 0.55f, 0.14f))
            } else {
                CoverColors(tr("Custom"), ground = hsl(0f, 0.46f, 0.90f), accent = hsl(0f, 0.62f, 0.30f), ink = hsl(0f, 0.50f, 0.13f), accentInk = hsl(0f, 0.30f, 0.95f))
            }
        }
    }
}

/**
 * A cover's design: its [style] and [colors], the [genre] its picture is of, and the [seed] its picture
 * (and anything else drawn by chance) is drawn from.
 */
data class CoverDesign(val style: CoverStyle, val colors: CoverColors, val genre: CoverGenre = CoverGenre.LITERARY, val seed: Int = 0) {
    companion object {
        /**
         * A design picked by chance: for a [genre], mostly one of the styles that suit it; in a built-in
         * theme that suits the style or (with [anyColour]) sometimes a colour of its own.
         */
        fun random(rnd: Random = Random.Default, anyColour: Boolean = true, genre: CoverGenre? = null): CoverDesign {
            val style = if (genre != null && rnd.nextInt(5) != 0) CoverStyle.entries.filter { genre in it.suits }.ifEmpty { CoverStyle.entries }.random(rnd)
            else CoverStyle.entries.random(rnd)
            return CoverDesign(style, colorsFor(style, rnd, anyColour), genre ?: CoverGenre.LITERARY, rnd.nextInt())
        }

        /** Colours that suit [style]: the dark-board designs get a dark theme, the pale ones a pale one. */
        fun colorsFor(style: CoverStyle, rnd: Random = Random.Default, anyColour: Boolean = true): CoverColors {
            val wantsDark = when (style) {
                CoverStyle.FRAMED, CoverStyle.GILDED, CoverStyle.NOIR, CoverStyle.GOTHIC, CoverStyle.STARFIELD, CoverStyle.DECO -> true
                CoverStyle.BLOSSOM, CoverStyle.ESSAY -> false
                else -> null
            }
            if (anyColour && rnd.nextInt(3) == 0) return CoverColors.fromHue(rnd.nextFloat() * 360f, 0.45f + rnd.nextFloat() * 0.55f, dark = wantsDark ?: rnd.nextBoolean())
            return when (wantsDark) {
                true -> CoverColors.dark
                false -> CoverColors.light
                null -> CoverColors.themes
            }.random(rnd)
        }

        /** A series' books in turn get the dark boards in turn, as a reissued series is often coloured. */
        fun boardFor(seriesIndex: Double?): CoverColors? {
            val i = seriesIndex?.toInt()?.takeIf { it >= 1 } ?: return null
            val boards = CoverColors.dark
            return boards[(i - 1) % boards.size]
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
    private val roman = face(R.font.cinzel_variable, 600, false, R.font.liberation_serif_bold)
    private val romanLight = face(R.font.cinzel_variable, 500, false, R.font.liberation_serif_regular)
    private val romanDecorated = face(R.font.cinzel_decorative_bold, 700, false, R.font.liberation_serif_bold)
    private val blackLetter = face(R.font.unifraktur_maguntia, 400, false, R.font.liberation_serif_bold)
    private val script = face(R.font.great_vibes, 400, false, R.font.liberation_serif_regular)
    private val condensed = face(R.font.bebas_neue, 400, false, R.font.liberation_sans_bold)
    private val space = face(R.font.orbitron_variable, 700, false, R.font.liberation_sans_bold)
    private val spaceLight = face(R.font.orbitron_variable, 500, false, R.font.liberation_sans_regular)
    private val deco = face(R.font.poiret_one, 400, false, R.font.liberation_sans_regular)
    private val rounded = face(R.font.fredoka_variable, 600, false, R.font.liberation_sans_bold)

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
            Typeface.create(if (spare == R.font.liberation_sans_regular || spare == R.font.liberation_sans_bold) Typeface.SANS_SERIF else Typeface.SERIF, weight, italic)
        }
    }

    /**
     * Draws the cover at [width] × [height] (the Kobo Clara's screen size by default). A design with a
     * picture shows [picture] there, placed by [crop], in place of the one painted for its genre.
     */
    fun render(
        text: CoverText, design: CoverDesign, width: Int = CoverCrop.OUT_WIDTH, height: Int = CoverCrop.OUT_HEIGHT,
        picture: Bitmap? = null, crop: CoverCrop = CoverCrop(),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sheet = Sheet(Canvas(bmp), width.toFloat(), height.toFloat(), design.colors)
        val title = text.title.trim().ifEmpty { tr("Untitled") }
        val author = authorLine(text)
        val words = Words(
            title, seriesLine(text), author, seed = "$title\u0000${author.orEmpty()}".hashCode(),
            seriesName = text.series?.trim()?.takeIf { it.isNotEmpty() }, seriesIndex = text.seriesIndex,
        )
        val art = Art(design.genre, design.seed, picture, crop)
        when (design.style) {
            CoverStyle.LABEL -> label(sheet, words)
            CoverStyle.CLASSIC -> classic(sheet, words)
            CoverStyle.SUNRISE -> sunrise(sheet, words)
            CoverStyle.SPLIT -> split(sheet, words)
            CoverStyle.MOSAIC -> mosaic(sheet, words)
            CoverStyle.BOLD -> bold(sheet, words)
            CoverStyle.FRAMED -> framed(sheet, words, art)
            CoverStyle.GILDED -> gilded(sheet, words, art)
            CoverStyle.PAINTING -> painting(sheet, words, art)
            CoverStyle.NOIR -> noir(sheet, words, art)
            CoverStyle.GOTHIC -> gothic(sheet, words, art)
            CoverStyle.STARFIELD -> starfield(sheet, words, art)
            CoverStyle.DECO -> deco(sheet, words, art)
            CoverStyle.BLOSSOM -> blossom(sheet, words, art)
            CoverStyle.STORYBOOK -> storybook(sheet, words, art)
            CoverStyle.ESSAY -> essay(sheet, words, art)
        }
        return bmp
    }

    // ---- the words -----------------------------------------------------------------------------

    private class Words(val title: String, val series: String?, val author: String?, val seed: Int, val seriesName: String? = null, val seriesIndex: Double? = null)

    /** The picture a design shows: the reader's own ([picture], placed by [crop]) or one painted for [genre] from [seed]. */
    private class Art(val genre: CoverGenre, val seed: Int, val picture: Bitmap?, val crop: CoverCrop)

    /** Paints [art] into [r]: the reader's picture where it's placed, or the genre's scene. */
    private fun paint(canvas: Canvas, r: RectF, art: Art) {
        val pic = art.picture
        if (pic == null) {
            CoverArt.scene(canvas, r, art.genre, art.seed)
            return
        }
        val at = art.crop.copy(fit = CoverFit.FILL).place(r.width(), r.height(), pic.width, pic.height)
        // Halved first while it's much bigger than it's drawn, so it shrinks smoothly.
        var drawn: Bitmap = pic
        while (drawn.width > at.width * 2 && drawn.height > at.height * 2 && drawn.width > 1 && drawn.height > 1) {
            drawn = Bitmap.createScaledBitmap(drawn, drawn.width / 2, drawn.height / 2, true)
        }
        canvas.save()
        canvas.clipRect(r)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(drawn, null, RectF(r.left + at.left, r.top + at.top, r.left + at.left + at.width, r.top + at.top + at.height), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }

    /** "Sherlock Holmes · Book 5", or just the series when it has no number. */
    private fun seriesLine(t: CoverText): String? {
        val name = t.series?.trim().orEmpty()
        if (name.isEmpty()) return null
        val number = t.seriesIndex ?: return name
        return tr("{0} · Book {1}", name, Names.formatIndex(number))
    }

    /** Everyone who wrote it on one line: "A", "A & B", "A, B & C", and past three, "A, B & others". */
    private fun authorLine(t: CoverText): String? {
        val names = t.authors.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(tr("Unknown"), ignoreCase = true) }
        return when (names.size) {
            0 -> null
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            3 -> "${names[0]}, ${names[1]} & ${names[2]}"
            else -> tr("{0}, {1} & others", names[0], names[1])
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

        /** The widest line's width. */
        val width: Float get() = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }

        /**
         * Draws it in [colour], or with [shader] (laid over the whole cover, wherever the words are);
         * with an [outline] round the letters, and a [shadow] (or glow) under them.
         */
        fun draw(
            canvas: Canvas, x: Float, y: Float, colour: Int, shader: Shader? = null, outline: Int? = null, outlineWidth: Float = 0f,
            shadow: Int? = null, shadowRadius: Float = 0f, shadowDy: Float = 0f,
        ) {
            canvas.save()
            canvas.translate(x, y - capTop)
            val p = layout.paint
            if (outline != null) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = outlineWidth
                p.strokeJoin = Paint.Join.ROUND
                p.color = outline
                if (shadow != null) p.setShadowLayer(shadowRadius, 0f, shadowDy, shadow)
                layout.draw(canvas)
                p.clearShadowLayer()
                p.style = Paint.Style.FILL
            } else if (shadow != null) p.setShadowLayer(shadowRadius, 0f, shadowDy, shadow)
            p.color = colour
            if (shader != null) {
                shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-x, -(y - capTop)) })
                p.shader = shader
            }
            layout.draw(canvas)
            p.shader = null
            p.clearShadowLayer()
            canvas.restore()
        }
    }

    /**
     * Sets [text] in a box [width] wide: at [largest] if it fits in [height] without a word being
     * broken in two, otherwise at the biggest size down to [smallest] that does. Text that is too
     * long even then is cut short with an ellipsis. [pitch] is the distance between lines and
     * [tracking] the extra space between letters, both in ems.
     */
    @SuppressLint("WrongConstant") // Layout's break strategy is the same value as LineBreaker's
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

    // ---- the designs with pictures, gold leaf and lettering of their own ----------------------------

    /** Where [style]'s picture goes on a cover [w] × [h] (for placing a picture of the reader's own in it); null if it has none. */
    fun pictureFrame(style: CoverStyle, w: Float, h: Float): RectF? {
        val u = w / 100f
        return when (style) {
            CoverStyle.FRAMED -> RectF(12f * u, 33f * u, 88f * u, (h - 44f * u).coerceIn(50f * u, 91f * u))
            CoverStyle.PAINTING -> RectF(0f, 0f, w, h)
            else -> null
        }
    }

    private val englishNumbers = listOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen",
        "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen", "Twenty",
    )

    /** "Book Six of", as a series' place is given on its covers (in figures in other languages). */
    private fun bookOf(index: Double): String {
        val whole = index.toInt().takeIf { it.toDouble() == index && it in 1..20 }
        val n = if (whole != null && dev.kobolibrary.app.L10n.language == "en") englishNumbers[whole] else Names.formatIndex(index)
        return tr("Book {0} of", n)
    }

    private fun whole(s: Sheet) = RectF(0f, 0f, s.w, s.h)

    /** A plain board in the ground colour, lit a little from above, with a fine grain. */
    private fun board(s: Sheet, seed: Int, vignette: Int = 90) {
        s.canvas.drawColor(s.c.ground)
        val p = Painter(s.canvas, whole(s), Random(seed))
        p.glow(s.w / 2, s.h * 0.3f, s.w * 0.9f, Color.WHITE, 22)
        p.grain(1f)
        p.vignette(vignette)
    }

    /** Book cloth: the ground colour woven fine (short threads every way, catching the light or not), darker at the edges. */
    private fun cloth(s: Sheet, seed: Int) {
        s.canvas.drawColor(s.c.ground)
        val rnd = Random(seed)
        val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = kotlin.math.max(1f, s.u * 0.16f); strokeCap = Paint.Cap.ROUND }
        // As many threads for the area at any size (so a small copy is quick to draw).
        val n = (s.w * s.h * 0.08f).toInt().coerceAtMost(140000)
        repeat(n) {
            val x = rnd.nextFloat() * s.w
            val y = rnd.nextFloat() * s.h
            val len = s.u * (0.5f + rnd.nextFloat() * 1.4f)
            thread.color = faint(if (rnd.nextBoolean()) Color.WHITE else Color.BLACK, 7 + rnd.nextInt(10))
            if (rnd.nextBoolean()) s.canvas.drawLine(x, y, x + len, y, thread) else s.canvas.drawLine(x, y, x, y + len, thread)
        }
        val p = Painter(s.canvas, whole(s), rnd)
        p.glow(s.w * 0.4f, s.h * 0.25f, s.w, Color.WHITE, 18)
        p.vignette(if (ColorUtils.calculateLuminance(s.c.ground) > 0.4) 45 else 115)
    }

    /** Gold leaf from [base]: light and dark bands across the cover, as foil catches the light. */
    private fun foil(s: Sheet, base: Int): Shader {
        val light = mix(base, Color.WHITE, 0.55f)
        val dark = mix(base, Color.BLACK, 0.4f)
        return LinearGradient(0f, 0f, s.w * 0.55f, s.h * 0.4f, intArrayOf(dark, base, light, base, dark, base, light, base), null, Shader.TileMode.MIRROR)
    }

    /** A small ornament: a lozenge between two tapering rules ending in dots. */
    private fun ornament(s: Sheet, cx: Float, cy: Float, colour: Int) {
        val u = s.u
        s.diamond(cx, cy, 1.3f * u, colour)
        s.diamond(cx, cy, 0.55f * u, s.c.ground)
        for (side in listOf(-1f, 1f)) {
            val path = Path().apply {
                moveTo(cx + side * 2.3f * u, cy - 0.3f * u)
                lineTo(cx + side * 9f * u, cy)
                lineTo(cx + side * 2.3f * u, cy + 0.3f * u)
                close()
            }
            s.canvas.drawPath(path, s.fill(colour))
            s.canvas.drawCircle(cx + side * 10.2f * u, cy, 0.6f * u, s.fill(colour))
        }
    }

    /**
     * A dark board in a ruled frame: the author's name large at the head, a framed picture, the title,
     * and "Book Six of" over the series' name.
     */
    private fun framed(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        board(s, art.seed)
        val gold = s.c.accent
        val shade = faint(Color.BLACK, 130)
        val o = 3.6f * u
        c.drawRect(o, o, s.w - o, s.h - o, s.stroke(faint(gold, 190), 0.24f * u))
        val i = 4.7f * u
        c.drawRect(i, i, s.w - i, s.h - i, s.stroke(faint(gold, 120), 0.12f * u))
        val cx = s.w / 2
        ornament(s, cx, 8f * u, gold)

        val frame = pictureFrame(CoverStyle.FRAMED, s.w, s.h)!!
        val authorTop = 12f * u
        val authorRoom = frame.top - 3.4f * u - authorTop
        t.author?.let { a -> fit(caps(a), roman, 12.5f * u, 4.4f * u, 82f * u, authorRoom, pitch = 1.06f, tracking = 0.03f) }?.let { f ->
            f.draw(c, cx - 41f * u, authorTop + (authorRoom - f.height) / 2, gold, shadow = shade, shadowRadius = 0.5f * u, shadowDy = 0.3f * u)
        }

        paint(c, frame, art)
        c.drawRect(frame, s.stroke(faint(Color.BLACK, 110), 0.7f * u))
        c.drawRect(frame.left - 0.9f * u, frame.top - 0.9f * u, frame.right + 0.9f * u, frame.bottom + 0.9f * u, s.stroke(faint(gold, 210), 0.22f * u))

        val below = frame.bottom + 4.4f * u
        val bottom = s.h - 7.5f * u
        val name = t.seriesName
        val seriesF = name?.let { fit(caps(it), romanLight, 3.9f * u, 2.5f * u, 76f * u, 5.5f * u, tracking = 0.2f) }
        val bookF = if (name != null && t.seriesIndex != null) fit(bookOf(t.seriesIndex), serifItalic, 3.9f * u, 2.5f * u, 50f * u, 5.5f * u) else null
        val tail = (bookF?.let { it.height + 2.6f * u } ?: 0f) + (seriesF?.let { it.height + 3.4f * u } ?: 0f)
        val title = fit(caps(t.title), roman, 9f * u, 4f * u, 84f * u, bottom - below - tail, pitch = 1.1f, tracking = 0.02f)!!
        var y = below + kotlin.math.max(0f, (bottom - below - title.height - tail) / 2)
        title.draw(c, cx - 42f * u, y, gold, shadow = shade, shadowRadius = 0.5f * u, shadowDy = 0.3f * u)
        y += title.height + 3.4f * u
        bookF?.let { f ->
            f.draw(c, cx - 25f * u, y, s.c.ink)
            val gap = f.width / 2 + 2.4f * u
            val ruleY = y + f.height * 0.45f
            s.rule(cx - gap - 7f * u, ruleY, 14f * u, 0.18f * u, faint(gold, 210))
            s.rule(cx + gap + 7f * u, ruleY, 14f * u, 0.18f * u, faint(gold, 210))
            y += f.height + 2.6f * u
        }
        seriesF?.draw(c, cx - 38f * u, y, s.c.ink)
    }

    /** Cloth-bound, with a line drawing for the genre and the lettering stamped in gold. */
    private fun gilded(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        cloth(s, art.seed)
        val gold = s.c.accent
        val pressed = faint(Color.BLACK, 150)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = foil(s, gold)
            setShadowLayer(0.35f * u, 0.12f * u, 0.24f * u, pressed)
        }
        CoverArt.stamp(c, RectF(15f * u, 7f * u, 85f * u, 73f * u), art.genre, art.seed, ink)
        val cx = s.w / 2
        var y = 79f * u
        val title = fit(t.title, romanDecorated, 11f * u, 4.6f * u, 86f * u, 25f * u, pitch = 1.08f)!!
        title.draw(c, cx - 43f * u, y, gold, shader = foil(s, gold), shadow = pressed, shadowRadius = 0.35f * u, shadowDy = 0.24f * u)
        y += title.height + 6.2f * u
        t.author?.let { a -> fit(caps(a), roman, 4.6f * u, 2.8f * u, 80f * u, 10f * u, tracking = 0.14f, pitch = 1.4f) }?.let { f ->
            f.draw(c, cx - 40f * u, y, gold, shader = foil(s, gold), shadow = pressed, shadowRadius = 0.3f * u, shadowDy = 0.2f * u)
            y += f.height + 3.6f * u
        }
        t.series?.let { fit(caps(it), romanLight, 3f * u, 2.2f * u, 76f * u, 5f * u, tracking = 0.18f) }
            ?.draw(c, cx - 38f * u, y, gold, shader = foil(s, gold), shadow = pressed, shadowRadius = 0.25f * u, shadowDy = 0.15f * u)
    }

    /** A painting over the whole cover, the title over its sky and the author at the foot. */
    private fun painting(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        paint(c, whole(s), art)
        c.drawRect(0f, 0f, s.w, s.h * 0.42f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.h * 0.42f, faint(Color.BLACK, 150), faint(Color.BLACK, 0), Shader.TileMode.CLAMP) })
        c.drawRect(0f, s.h * 0.72f, s.w, s.h, Paint().apply { shader = LinearGradient(0f, s.h * 0.72f, 0f, s.h, faint(Color.BLACK, 0), faint(Color.BLACK, 190), Shader.TileMode.CLAMP) })
        val cream = Color.rgb(0xF6, 0xEE, 0xDC)
        val shade = faint(Color.BLACK, 180)
        val cx = s.w / 2
        var y = 8f * u
        t.series?.let { fit(caps(it), romanLight, 3.2f * u, 2.3f * u, 80f * u, 5f * u, tracking = 0.2f) }?.let { f ->
            f.draw(c, cx - 40f * u, y, faint(cream, 230), shadow = shade, shadowRadius = 0.6f * u)
            y += f.height + 3.6f * u
        }
        fit(caps(t.title), roman, 11.5f * u, 4.6f * u, 86f * u, 32f * u, pitch = 1.08f)!!
            .draw(c, cx - 43f * u, y, cream, shadow = shade, shadowRadius = 1.2f * u, shadowDy = 0.4f * u)
        t.author?.let { fit(caps(it), roman, 4.8f * u, 2.8f * u, 80f * u, 12f * u, tracking = 0.14f, pitch = 1.4f) }?.let { f ->
            f.draw(c, cx - 40f * u, s.h - 8f * u - f.height, cream, shadow = shade, shadowRadius = 0.8f * u)
        }
    }

    /** Light through blinds, a man in a hat, a stripe of colour and tall narrow capitals. */
    private fun noir(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        val night = mix(s.c.ground, Color.BLACK, 0.75f)
        c.drawColor(night)
        val p = Painter(c, whole(s), Random(art.seed))
        p.glow(s.w * 0.3f, s.h * 0.32f, s.w * 0.95f, Color.WHITE, 46)
        c.save()
        c.rotate(-22f, s.w / 2, s.h / 2)
        val slat = 4.4f * u
        val light = Paint().apply { color = faint(Color.WHITE, 24) }
        var y = -s.h * 0.25f
        while (y < s.h * 0.78f) {
            c.drawRect(-s.w * 0.4f, y, s.w * 1.4f, y + slat * 0.55f, light)
            y += slat
        }
        c.restore()
        val man = Path()
        p.inHat(man, s.w * 0.7f, s.h + 2f * u, s.h * 0.66f)
        c.drawPath(man, s.fill(Color.rgb(7, 7, 9)))
        p.grain(0.9f)
        val accent = s.c.accent
        c.drawRect(8f * u, 8f * u, 9.4f * u, 48f * u, s.fill(accent))
        val paper = Color.rgb(0xF2, 0xEE, 0xE6)
        var ty = 8f * u
        t.author?.let { fit(caps(it), condensed, 6.2f * u, 3.6f * u, 62f * u, 13f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 1.05f, tracking = 0.08f) }?.let { f ->
            f.draw(c, 12.5f * u, ty, paper)
            ty += f.height + 5f * u
        }
        val title = fit(caps(t.title), condensed, 22f * u, 7f * u, 60f * u, 66f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 0.92f, tracking = 0.01f)!!
        title.draw(c, 12.5f * u, ty, paper, shadow = faint(Color.BLACK, 220), shadowRadius = 1f * u)
        ty += title.height + 3.6f * u
        t.series?.let { fit(caps(it), condensed, 4.2f * u, 3f * u, 60f * u, 8f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.12f) }?.draw(c, 12.5f * u, ty, accent)
    }

    /** A pointed window on a haunted night, and black-letter lettering. */
    private fun gothic(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        board(s, art.seed, vignette = 140)
        val gold = s.c.accent
        val l = 20f * u
        val r = 80f * u
        val span = r - l
        val bottom = s.h - 22f * u
        val spring = bottom - 24f * u
        val apex = spring - span * 0.866f
        val arch = Path().apply {
            moveTo(l, bottom)
            lineTo(l, spring)
            arcTo(RectF(r - span, spring - span, r + span, spring + span), 180f, 60f)
            arcTo(RectF(l - span, spring - span, l + span, spring + span), 300f, 60f)
            lineTo(r, bottom)
            close()
        }
        c.save()
        c.clipPath(arch)
        paint(c, RectF(l, apex, r, bottom), Art(CoverGenre.HORROR, art.seed, null, art.crop))
        c.restore()
        c.drawPath(arch, s.stroke(faint(gold, 220), 0.5f * u))
        val outer = Path().apply {
            val g = 1.6f * u
            moveTo(l - g, bottom + g)
            lineTo(l - g, spring)
            arcTo(RectF(r - span - g, spring - span - g, r + span + g, spring + span + g), 180f, 60f)
            arcTo(RectF(l - span - g, spring - span - g, l + span + g, spring + span + g), 300f, 60f)
            lineTo(r + g, bottom + g)
            close()
        }
        c.drawPath(outer, s.stroke(faint(gold, 140), 0.2f * u))
        s.diamond(s.w / 2, apex - 3.4f * u, 1f * u, gold)
        val cx = s.w / 2
        val bone = mix(s.c.ink, Color.WHITE, 0.3f)
        val glow = faint(Color.BLACK, 220)
        val roomTop = 7f * u
        val roomBottom = apex - 6f * u
        val series = t.series?.let { fit(caps(it), romanLight, 3f * u, 2.2f * u, 80f * u, 5f * u, tracking = 0.2f) }
        val title = fit(t.title, blackLetter, 13f * u, 5f * u, 88f * u, roomBottom - roomTop - (series?.let { it.height + 3f * u } ?: 0f), pitch = 1.05f)!!
        var y = roomTop + kotlin.math.max(0f, (roomBottom - roomTop - title.height - (series?.let { it.height + 3f * u } ?: 0f)) / 2)
        title.draw(c, cx - 44f * u, y, bone, shadow = glow, shadowRadius = 1.2f * u)
        y += title.height + 3f * u
        series?.draw(c, cx - 40f * u, y, faint(gold, 230))
        t.author?.let { fit(caps(it), roman, 4.4f * u, 2.8f * u, 80f * u, 11f * u, tracking = 0.16f, pitch = 1.4f) }?.let { f ->
            f.draw(c, cx - 40f * u, bottom + (s.h - bottom - f.height) / 2 + 0.6f * u, bone)
        }
    }

    /** Deep space: stars, a nebula's glow, and the curve of a planet along the foot. */
    private fun starfield(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        val deep = mix(s.c.ground, Color.BLACK, 0.82f)
        val p = Painter(c, whole(s), Random(art.seed))
        p.sky(intArrayOf(deep, mix(deep, s.c.ground, 0.5f), deep))
        val glowC = s.c.accent
        val second = ColorUtils.blendARGB(glowC, Color.rgb(0xB0, 0x3A, 0x8A), 0.6f)
        repeat(4) { k ->
            p.glow(p.x(p.rnd.nextFloat()), p.y(p.rnd.nextFloat() * 0.62f), s.w * (0.28f + p.rnd.nextFloat() * 0.3f), if (k % 2 == 0) glowC else second, 58)
        }
        p.stars(400, 1f)
        val pr = s.w * 1.1f
        val pcx = s.w * 0.5f
        val pcy = s.h + pr * 0.62f
        p.glow(pcx, pcy, pr * 1.16f, glowC, 140)
        c.drawCircle(pcx, pcy, pr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(pcx, pcy - pr * 0.95f, pr * 1.25f, intArrayOf(mix(glowC, deep, 0.45f), mix(deep, Color.BLACK, 0.4f)), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(pcx, pcy, pr, s.stroke(faint(mix(glowC, Color.WHITE, 0.55f), 210), 0.45f * u))
        val cx = s.w / 2
        var y = 13f * u
        t.series?.let { fit(caps(it), spaceLight, 2.8f * u, 2.1f * u, 82f * u, 5f * u, tracking = 0.3f) }?.let { f ->
            f.draw(c, cx - 41f * u, y, glowC)
            y += f.height + 4.4f * u
        }
        fit(caps(t.title), space, 9.5f * u, 3.8f * u, 88f * u, 36f * u, pitch = 1.16f, tracking = 0.08f)!!
            .draw(c, cx - 44f * u, y, Color.WHITE, shadow = glowC, shadowRadius = 2.4f * u)
        t.author?.let { fit(caps(it), spaceLight, 3.8f * u, 2.4f * u, 82f * u, 10f * u, tracking = 0.25f, pitch = 1.5f) }?.let { f ->
            f.draw(c, cx - 41f * u, s.h - 9f * u - f.height, Color.WHITE)
        }
    }

    /** A rectangle with its corners stepped in, as framed in the 1920s. */
    private fun steppedFrame(s: Sheet, inset: Float, step: Float, paint: Paint) {
        val l = inset
        val t = inset
        val r = s.w - inset
        val b = s.h - inset
        val path = Path().apply {
            moveTo(l + step, t)
            lineTo(r - step, t); lineTo(r - step, t + step); lineTo(r, t + step)
            lineTo(r, b - step); lineTo(r - step, b - step); lineTo(r - step, b)
            lineTo(l + step, b); lineTo(l + step, b - step); lineTo(l, b - step)
            lineTo(l, t + step); lineTo(l + step, t + step)
            close()
        }
        s.canvas.drawPath(path, paint)
    }

    /** Gold sunbursts and stepped frames, with a panel for the words in thin geometric capitals. */
    private fun deco(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        board(s, art.seed, vignette = 60)
        val gold = s.c.accent
        val cx = s.w / 2
        val sy = s.h * 1.02f
        for (k in 0..36) {
            val a = Math.toRadians(180.0 + k * 5.0)
            c.drawLine(cx + (kotlin.math.cos(a) * 30f * u).toFloat(), sy + (kotlin.math.sin(a) * 30f * u).toFloat(),
                cx + (kotlin.math.cos(a) * s.h).toFloat(), sy + (kotlin.math.sin(a) * s.h).toFloat(), s.stroke(faint(gold, if (k % 2 == 0) 130 else 60), 0.18f * u))
        }
        for (k in 0 until 4) c.drawCircle(cx, sy, (18f + k * 5.5f) * u, s.stroke(faint(gold, 170), 0.25f * u))
        steppedFrame(s, 4.2f * u, 4f * u, s.stroke(gold, 0.3f * u))
        steppedFrame(s, 5.6f * u, 3.2f * u, s.stroke(faint(gold, 140), 0.14f * u))
        val panel = RectF(15f * u, 22f * u, 85f * u, 98f * u)
        c.drawRect(panel, s.fill(s.c.ground))
        c.drawRect(panel, s.stroke(gold, 0.3f * u))
        c.drawRect(panel.left + 1.3f * u, panel.top + 1.3f * u, panel.right - 1.3f * u, panel.bottom - 1.3f * u, s.stroke(faint(gold, 150), 0.14f * u))
        // A fan over the panel.
        val fanR = 10f * u
        c.drawArc(RectF(cx - fanR, panel.top - fanR, cx + fanR, panel.top + fanR), 180f, 180f, true, s.fill(s.c.ground))
        c.drawArc(RectF(cx - fanR, panel.top - fanR, cx + fanR, panel.top + fanR), 180f, 180f, false, s.stroke(gold, 0.3f * u))
        for (k in 1..7) {
            val a = Math.toRadians(180.0 + k * 22.5)
            c.drawLine(cx, panel.top, cx + (kotlin.math.cos(a) * fanR).toFloat(), panel.top + (kotlin.math.sin(a) * fanR).toFloat(), s.stroke(faint(gold, 200), 0.2f * u))
        }
        c.drawArc(RectF(cx - fanR * 0.45f, panel.top - fanR * 0.45f, cx + fanR * 0.45f, panel.top + fanR * 0.45f), 180f, 180f, true, s.fill(gold))

        val wide = 62f * u
        val series = t.series?.let { fit(it, serifItalic, 3.6f * u, 2.6f * u, wide, 6f * u) }
        val author = t.author?.let { fit(caps(it), deco, 4.2f * u, 2.6f * u, wide, 10f * u, tracking = 0.3f, pitch = 1.45f) }
        val room = panel.height() - 18f * u - (series?.let { it.height + 4f * u } ?: 0f) - (author?.let { it.height + 8f * u } ?: 0f)
        val title = fit(caps(t.title), deco, 10f * u, 4.2f * u, wide, room, pitch = 1.2f, tracking = 0.1f)!!
        val block = title.height + (series?.let { it.height + 4f * u } ?: 0f) + (author?.let { it.height + 8f * u } ?: 0f)
        var y = panel.top + (panel.height() - block) / 2 + 2f * u
        series?.let {
            it.draw(c, cx - wide / 2, y, faint(s.c.ink, 220))
            y += it.height + 4f * u
        }
        title.draw(c, cx - wide / 2, y, gold)
        y += title.height + 4f * u
        if (author != null) {
            s.diamond(cx, y, 0.9f * u, gold)
            s.rule(cx - 7f * u, y, 9f * u, 0.18f * u, gold)
            s.rule(cx + 7f * u, y, 9f * u, 0.18f * u, gold)
            author.draw(c, cx - wide / 2, y + 4f * u, s.c.ink)
        }
    }

    /** Blossoming branches in from two corners, soft washes of colour, and a flowing script. */
    private fun blossom(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        c.drawColor(s.c.ground)
        val p = Painter(c, whole(s), Random(art.seed))
        repeat(7) { p.glow(p.x(p.rnd.nextFloat()), p.y(p.rnd.nextFloat()), s.w * (0.2f + p.rnd.nextFloat() * 0.3f), s.c.accent, 32) }
        p.grain(0.5f)
        val petal = mix(s.c.accent, Color.WHITE, 0.55f)
        val bark = mix(s.c.ink, s.c.accent, 0.2f)
        p.blossomBranch(-2f * u, 5f * u, 54f * u, 22f * u, bark, petal, s.c.accent)
        p.blossomBranch(s.w + 2f * u, s.h - 7f * u, 46f * u, s.h - 25f * u, bark, petal, s.c.accent)
        val cx = s.w / 2
        val wide = 80f * u
        val series = t.series?.let { fit(it, serifItalic, 3.8f * u, 2.6f * u, wide, 6f * u) }
        val author = t.author?.let { fit(caps(it), romanLight, 3.6f * u, 2.4f * u, wide, 9f * u, tracking = 0.22f, pitch = 1.5f) }
        val top = 34f * u
        val bottom = s.h - 34f * u
        val room = bottom - top - (series?.let { it.height + 4f * u } ?: 0f) - (author?.let { it.height + 7f * u } ?: 0f)
        val title = fit(t.title, script, 17f * u, 6f * u, wide, room, pitch = 1.0f)!!
        val block = title.height + (series?.let { it.height + 4f * u } ?: 0f) + (author?.let { it.height + 7f * u } ?: 0f)
        var y = top + (bottom - top - block) / 2
        series?.let {
            it.draw(c, cx - wide / 2, y, s.c.accent)
            y += it.height + 4f * u
        }
        title.draw(c, cx - wide / 2, y, mix(s.c.accent, s.c.ink, 0.25f))
        y += title.height + 3.5f * u
        if (author != null) {
            s.rule(cx, y, 10f * u, 0.2f * u, faint(s.c.accent, 200))
            author.draw(c, cx - wide / 2, y + 3.5f * u, s.c.ink)
        }
    }

    /** A sunny storybook land, with the title in big round letters. */
    private fun storybook(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        CoverArt.scene(c, whole(s), CoverGenre.CHILDREN, art.seed)
        val cx = s.w / 2
        val navy = Color.rgb(0x2B, 0x3A, 0x67)
        var y = 7f * u
        val title = fit(t.title, rounded, 14f * u, 5f * u, 84f * u, 34f * u, pitch = 1.05f)!!
        title.draw(c, cx - 42f * u, y, Color.WHITE, outline = navy, outlineWidth = 1.8f * u, shadow = faint(Color.BLACK, 90), shadowRadius = 0.8f * u, shadowDy = 0.6f * u)
        y += title.height + 3f * u
        t.series?.let { fit(it, rounded, 3.8f * u, 2.6f * u, 76f * u, 6f * u) }?.draw(c, cx - 38f * u, y, Color.WHITE, outline = navy, outlineWidth = 0.9f * u)
        t.author?.let { fit(it, rounded, 4.6f * u, 3f * u, 70f * u, 10f * u, pitch = 1.3f) }?.let { f ->
            val bh = f.height + 4f * u
            val bw = f.width + 9f * u
            val top = s.h - 7f * u - bh
            c.drawRoundRect(cx - bw / 2, top, cx + bw / 2, top + bh, bh / 2, bh / 2, s.fill(faint(Color.WHITE, 235)))
            f.draw(c, cx - 35f * u, top + 2f * u, navy)
        }
    }

    /** A block of colour with rings and arcs over plain modern lettering, as non-fiction is often dressed. */
    private fun essay(s: Sheet, t: Words, art: Art) {
        val u = s.u
        val c = s.canvas
        c.drawColor(s.c.ground)
        val blockBottom = 60f * u
        c.drawRect(0f, 0f, s.w, blockBottom, s.fill(s.c.accent))
        c.save()
        c.clipRect(0f, 0f, s.w, blockBottom)
        val rnd = Random(art.seed)
        val ccx = s.w * (0.55f + rnd.nextFloat() * 0.25f)
        val ccy = 32f * u
        for (k in 0 until 6) c.drawCircle(ccx, ccy, (8f + k * 7f) * u, s.stroke(faint(s.c.accentInk, 80), 0.35f * u))
        c.drawArc(RectF(ccx - 36f * u, ccy - 36f * u, ccx + 36f * u, ccy + 36f * u), 200f, 110f, false, s.stroke(s.c.accentInk, 2.4f * u))
        c.drawArc(RectF(ccx - 22f * u, ccy - 22f * u, ccx + 22f * u, ccy + 22f * u), 20f, 130f, false, s.stroke(faint(s.c.accentInk, 170), 1.6f * u))
        c.drawCircle(ccx - 18f * u, ccy + 10f * u, 5.5f * u, s.fill(faint(s.c.accentInk, 210)))
        c.drawCircle(ccx + 21f * u, ccy - 19f * u, 2.8f * u, s.fill(s.c.ground))
        c.restore()
        val x = 9f * u
        var y = blockBottom + 7f * u
        t.series?.let { fit(caps(it), sans, 3f * u, 2.2f * u, 82f * u, 6f * u, Layout.Alignment.ALIGN_NORMAL, tracking = 0.14f, pitch = 1.5f) }?.let { f ->
            f.draw(c, x, y, s.c.accent)
            y += f.height + 3.6f * u
        }
        val author = t.author?.let { fit(it, sans, 4.4f * u, 2.6f * u, 82f * u, 12f * u, Layout.Alignment.ALIGN_NORMAL, pitch = 1.35f) }
        val title = fit(t.title, sansBold, 10.5f * u, 4.4f * u, 82f * u, s.h - y - 12f * u - (author?.let { it.height + 8f * u } ?: 0f), Layout.Alignment.ALIGN_NORMAL, pitch = 1.12f)!!
        title.draw(c, x, y, s.c.ink)
        s.rect(x, y + title.height + 4f * u, x + 12f * u, y + title.height + 4.8f * u, s.c.accent)
        author?.draw(c, x, s.h - 9f * u - author.height, s.c.ink)
    }
}
