package dev.kobolibrary.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kobolibrary.app.R

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
