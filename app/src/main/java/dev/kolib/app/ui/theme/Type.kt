package dev.kolib.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kolib.app.R

/**
 * EB Garamond, the typeface of KoLib's name in the opening, for the app's headings and book titles: a
 * file for each weight it's used at (cut down to the Latin, Greek and Cyrillic alphabets).
 */
val Garamond = FontFamily(
    Font(R.font.eb_garamond_ui_medium, FontWeight.Medium),
    Font(R.font.eb_garamond_ui_semibold, FontWeight.SemiBold),
    Font(R.font.eb_garamond_ui_bold, FontWeight.Bold),
    Font(R.font.eb_garamond_ui_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

private val Base = Typography()

/**
 * Headings and titles in Garamond; everything else (buttons, labels, body text) stays in the device's
 * own sans-serif, which reads best small. Garamond's small letters are short for its size, so it's set
 * a little larger than the sans-serif it replaces, to look the same size beside it.
 */
val KoLibTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.Medium),
    displayMedium = Base.displayMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.Medium),
    displaySmall = Base.displaySmall.copy(fontFamily = Garamond, fontWeight = FontWeight.Medium),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 33.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = 0.1.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)

/** The lettering of a look's headings and titles. */
enum class Lettering { GARAMOND, GARAMOND_ITALIC, SANS, SANS_HEAVY, MONO, CONSOLE }

/** The device's narrow sans-serif, for the looks of consoles and machines. */
private val Condensed: FontFamily by lazy {
    runCatching { FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)) }.getOrDefault(FontFamily.SansSerif)
}

private fun headings(family: FontFamily, weight: FontWeight, style: FontStyle = FontStyle.Normal, spacing: Float = 0f, scale: Float = 1f) = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = family, fontWeight = weight, fontStyle = style),
    displayMedium = Base.displayMedium.copy(fontFamily = family, fontWeight = weight, fontStyle = style),
    displaySmall = Base.displaySmall.copy(fontFamily = family, fontWeight = weight, fontStyle = style),
    headlineLarge = Base.headlineLarge.copy(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = (32 * scale).sp, lineHeight = (40 * scale).sp, letterSpacing = spacing.sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = (28 * scale).sp, lineHeight = (35 * scale).sp, letterSpacing = spacing.sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = (24 * scale).sp, lineHeight = (31 * scale).sp, letterSpacing = spacing.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, letterSpacing = spacing.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = (17 * scale).sp, lineHeight = (23 * scale).sp, letterSpacing = spacing.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = family, fontWeight = weight, fontSize = (15 * scale).sp, lineHeight = (20 * scale).sp, letterSpacing = spacing.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)

private val GaramondItalicType = KoLibTypography.let { t ->
    // Flowing italic for the larger headings; the small titles in lists stay upright, to read at a glance.
    fun androidx.compose.ui.text.TextStyle.italic() = copy(fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
    t.copy(
        headlineLarge = t.headlineLarge.italic(), headlineMedium = t.headlineMedium.italic(), headlineSmall = t.headlineSmall.italic(),
        titleLarge = t.titleLarge.italic(), titleMedium = t.titleMedium.italic(),
    )
}
private val SansType = headings(FontFamily.SansSerif, FontWeight.SemiBold, spacing = -0.2f)
private val SansHeavyType = headings(FontFamily.SansSerif, FontWeight.Bold, spacing = -0.1f)
// A fixed-width letter is wide: set a little smaller, to take the room the others do.
private val MonoType = headings(FontFamily.Monospace, FontWeight.Medium, scale = 0.9f)
private val ConsoleType by lazy { headings(Condensed, FontWeight.Medium, spacing = 0.9f, scale = 1.04f) }

fun typographyFor(lettering: Lettering): Typography = when (lettering) {
    Lettering.GARAMOND -> KoLibTypography
    Lettering.GARAMOND_ITALIC -> GaramondItalicType
    Lettering.SANS -> SansType
    Lettering.SANS_HEAVY -> SansHeavyType
    Lettering.MONO -> MonoType
    Lettering.CONSOLE -> ConsoleType
}
