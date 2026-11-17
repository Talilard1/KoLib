package dev.kolib.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.kolib.app.ui.components.PenTapsOpenTheKeyboard
import kotlin.math.max

// ---- the gold of the opening -------------------------------------------------------------------

/** The opening's colours, which every look borrows from. */
object KoLibGold {
    val night = Color(0xFF1E120B)
    val glow = Color(0xFF46291A)
    val ember = Color(0xFF7A4A22)
    val light = Color(0xFFF6CF95)
    val gold = Color(0xFFE2B76A)
    val deep = Color(0xFFC98A45)
    val spark = Color(0xFFFFF3D6)
    /** Text and icons on gold. */
    val ink = Color(0xFF2A1806)
}

// ---- the looks --------------------------------------------------------------------------------

// Warm paper, ink, and gold leaf.
private val Light = lightColorScheme(
    primary = Color(0xFF87591A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DDB3),
    onPrimaryContainer = Color(0xFF2E1D02),
    secondary = Color(0xFF7A5C3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEDFC4),
    onSecondaryContainer = Color(0xFF3A2A12),
    tertiary = Color(0xFF4F6B3F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E9C6),
    onTertiaryContainer = Color(0xFF13240A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF1F1A14),
    surface = Color(0xFFFBF7EF),
    onSurface = Color(0xFF1F1A14),
    surfaceVariant = Color(0xFFEEE5D6),
    onSurfaceVariant = Color(0xFF5A4E40),
    surfaceTint = Color(0xFF87591A),
    outline = Color(0xFF857766),
    outlineVariant = Color(0xFFDCCFBC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1E6),
    surfaceContainer = Color(0xFFF2EADD),
    surfaceContainerHigh = Color(0xFFECE3D4),
    surfaceContainerHighest = Color(0xFFE6DCCB),
    surfaceBright = Color(0xFFFBF7EF),
    surfaceDim = Color(0xFFDED4C3),
    inverseSurface = Color(0xFF342E26),
    inverseOnSurface = Color(0xFFF8EFE2),
    inversePrimary = Color(0xFFE6BE78),
)

// The opening's own colours: gold on deep brown.
private val Dark = darkColorScheme(
    primary = Color(0xFFE6BE78),
    onPrimary = Color(0xFF2B1903),
    primaryContainer = Color(0xFF5B3F16),
    onPrimaryContainer = Color(0xFFFFE3B3),
    secondary = Color(0xFFDDB68C),
    onSecondary = Color(0xFF3A2410),
    secondaryContainer = Color(0xFF4A3420),
    onSecondaryContainer = Color(0xFFF6D9A8),
    tertiary = Color(0xFFB5CDA0),
    onTertiary = Color(0xFF1E3312),
    tertiaryContainer = Color(0xFF34472A),
    onTertiaryContainer = Color(0xFFD2E8C2),
    error = Color(0xFFF0A492),
    onError = Color(0xFF4D1408),
    errorContainer = Color(0xFF6E2A1C),
    onErrorContainer = Color(0xFFFFDAD2),
    background = Color(0xFF1A110B),
    onBackground = Color(0xFFF3E8D7),
    surface = Color(0xFF1A110B),
    onSurface = Color(0xFFF3E8D7),
    surfaceVariant = Color(0xFF4B3727),
    onSurfaceVariant = Color(0xFFCBB497),
    surfaceTint = Color(0xFFE6BE78),
    outline = Color(0xFF8E7458),
    outlineVariant = Color(0xFF4B3727),
    surfaceContainerLowest = Color(0xFF120B07),
    surfaceContainerLow = Color(0xFF21160F),
    surfaceContainer = Color(0xFF271A12),
    surfaceContainerHigh = Color(0xFF302117),
    surfaceContainerHighest = Color(0xFF3A2A1D),
    surfaceBright = Color(0xFF443222),
    surfaceDim = Color(0xFF1A110B),
    inverseSurface = Color(0xFFF3E8D7),
    inverseOnSurface = Color(0xFF2B1E15),
    inversePrimary = Color(0xFF87591A),
)

// Gold on true black: the screen's black parts are switched off on an OLED screen.
private val Oled = Dark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF2E2219),
    outlineVariant = Color(0xFF3A2B1F),
    secondaryContainer = Color(0xFF3A2918),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0705),
    surfaceContainer = Color(0xFF120D09),
    surfaceContainerHigh = Color(0xFF1A130E),
    surfaceContainerHighest = Color(0xFF241A13),
    surfaceBright = Color(0xFF2C2118),
    surfaceDim = Color(0xFF000000),
)

// Dark walnut shelves, brass fittings, oxblood leather and parchment.
private val Bookcase = darkColorScheme(
    primary = Color(0xFFE2B76A),
    onPrimary = Color(0xFF2E1D04),
    primaryContainer = Color(0xFF5E4314),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFE8A88C),
    onSecondary = Color(0xFF4A1A08),
    secondaryContainer = Color(0xFF66291A),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA9C79A),
    onTertiary = Color(0xFF16330E),
    tertiaryContainer = Color(0xFF2E4A26),
    onTertiaryContainer = Color(0xFFC5E4B5),
    error = Color(0xFFF2A08C),
    onError = Color(0xFF4D1408),
    errorContainer = Color(0xFF6E2A1C),
    onErrorContainer = Color(0xFFFFDAD2),
    background = Color(0xFF2A1B12),
    onBackground = Color(0xFFF1E3CC),
    surface = Color(0xFF2A1B12),
    onSurface = Color(0xFFF1E3CC),
    surfaceVariant = Color(0xFF4A3424),
    onSurfaceVariant = Color(0xFFDCC7AA),
    surfaceTint = Color(0xFFE2B76A),
    outline = Color(0xFFA68C6F),
    outlineVariant = Color(0xFF5C4431),
    surfaceContainerLowest = Color(0xFF20140D),
    surfaceContainerLow = Color(0xFF332217),
    surfaceContainer = Color(0xFF3B291C),
    surfaceContainerHigh = Color(0xFF463222),
    surfaceContainerHighest = Color(0xFF523B29),
    surfaceBright = Color(0xFF5A4330),
    surfaceDim = Color(0xFF2A1B12),
    inverseSurface = Color(0xFFF1E3CC),
    inverseOnSurface = Color(0xFF2B1C13),
    inversePrimary = Color(0xFF7A5A1F),
)

/** True in the Bookcase look: covers stand on wooden shelves. */
val LocalBookcase = staticCompositionLocalOf { false }

/** The wood of the shelves (Bookcase look): plank face, its lit top edge, and the back of the case. */
object ShelfColors {
    val plankTop = Color(0xFF9A6A40)
    val plankBottom = Color(0xFF5E3B22)
    val plankEdge = Color(0xFFC08B5A)
    val back = Color(0xFF22160F)
}

// ---- what the colour scheme can't say -----------------------------------------------------------

/** What's drawn behind the screens: a glow like the opening's, lamplight, paper, or plain black. */
enum class Backdrop { GLOW, LAMP, PAPER, BLACK }

/**
 * The parts of a look beyond Material's colour roles: the gold of the main buttons and the mark, and
 * what's drawn behind the screens.
 */
@Immutable
class KoLibStyle(
    val dark: Boolean,
    val backdrop: Backdrop,
    /** The main buttons' gold: one flat colour. */
    val gold: Color,
    /** Text and icons on [gold]. */
    val onGold: Color,
    /** The mark's gold, light at its top left to deep at its foot. */
    val markGold: List<Color>,
    /** A fine line in gold, for dividers and edges. */
    val hairline: Color,
    /** The badges on covers: a dark film, and their text. */
    val badge: Color,
    val onBadge: Color,
    /** Shadows' colour: warmer than black on paper. */
    val shadow: Color,
)

private val DarkStyle = KoLibStyle(
    dark = true,
    backdrop = Backdrop.GLOW,
    gold = Color(0xFFE8BE78),
    onGold = KoLibGold.ink,
    markGold = listOf(Color(0xFFFBE6BF), Color(0xFFEBC07E), KoLibGold.deep),
    hairline = KoLibGold.gold.copy(alpha = 0.20f),
    badge = Color.Black.copy(alpha = 0.64f),
    onBadge = Color(0xFFFFF3E0),
    shadow = Color.Black,
)

private val OledStyle = KoLibStyle(
    dark = true,
    backdrop = Backdrop.BLACK,
    gold = Color(0xFFE8BE78),
    onGold = KoLibGold.ink,
    markGold = listOf(Color(0xFFFBE6BF), Color(0xFFEBC07E), KoLibGold.deep),
    hairline = KoLibGold.gold.copy(alpha = 0.18f),
    badge = Color.Black.copy(alpha = 0.70f),
    onBadge = Color(0xFFFFF3E0),
    shadow = Color.Black,
)

private val BookcaseStyle = KoLibStyle(
    dark = true,
    backdrop = Backdrop.LAMP,
    gold = Color(0xFFE6BA70),
    onGold = KoLibGold.ink,
    markGold = listOf(Color(0xFFFBE6BF), Color(0xFFEBC07E), KoLibGold.deep),
    hairline = KoLibGold.gold.copy(alpha = 0.22f),
    badge = Color.Black.copy(alpha = 0.64f),
    onBadge = Color(0xFFFFF3E0),
    shadow = Color.Black,
)

private val LightStyle = KoLibStyle(
    dark = false,
    backdrop = Backdrop.PAPER,
    gold = Color(0xFFE5B567),
    onGold = KoLibGold.ink,
    markGold = listOf(Color(0xFFC99A55), Color(0xFFA06B28), Color(0xFF6E4614)),
    hairline = Color(0xFF87591A).copy(alpha = 0.20f),
    badge = Color(0xFF1F1A14).copy(alpha = 0.66f),
    onBadge = Color(0xFFFFF8EC),
    shadow = Color(0xFF5A3A12),
)

val LocalKoLibStyle = staticCompositionLocalOf { LightStyle }

@Composable
fun KoLibTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val (scheme, style) = when (choice) {
        ThemeChoice.SYSTEM -> if (systemDark) Dark to DarkStyle else Light to LightStyle
        ThemeChoice.LIGHT -> Light to LightStyle
        ThemeChoice.DARK -> Dark to DarkStyle
        ThemeChoice.OLED -> Oled to OledStyle
        ThemeChoice.BOOKCASE -> Bookcase to BookcaseStyle
    }
    CompositionLocalProvider(
        LocalBookcase provides (choice == ThemeChoice.BOOKCASE),
        LocalKoLibStyle provides style,
    ) {
        MaterialTheme(colorScheme = scheme, typography = KoLibTypography, content = content)
    }
}

/**
 * The look's background, with what's drawn on it: in Dark, a warm glow from the top like the opening's;
 * in Bookcase, lamplight on the wood; in Light, paper catching the light; in OLED black, nothing.
 */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val style = LocalKoLibStyle.current
    val background = MaterialTheme.colorScheme.background
    return drawWithCache {
        val w = size.width
        val h = size.height
        val big = max(w, h).coerceAtLeast(1f)
        val layers: List<Brush> = when (style.backdrop) {
            Backdrop.BLACK -> emptyList()
            Backdrop.GLOW -> listOf(
                Brush.radialGradient(
                    0f to KoLibGold.glow.copy(alpha = 0.62f),
                    0.45f to KoLibGold.glow.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.24f, -h * 0.10f),
                    radius = big * 0.95f,
                ),
                Brush.radialGradient(
                    0f to KoLibGold.ember.copy(alpha = 0.14f),
                    1f to Color.Transparent,
                    center = Offset(w * 1.02f, h * 1.06f),
                    radius = big * 0.62f,
                ),
            )
            Backdrop.LAMP -> listOf(
                Brush.radialGradient(
                    0f to Color(0xFF6E4629).copy(alpha = 0.50f),
                    0.5f to Color(0xFF6E4629).copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.5f, -h * 0.18f),
                    radius = big * 0.9f,
                ),
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.28f),
                ),
            )
            Backdrop.PAPER -> listOf(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.15f, -h * 0.12f),
                    radius = big * 0.9f,
                ),
                Brush.radialGradient(
                    0f to Color(0xFFE6D6BB).copy(alpha = 0.50f),
                    1f to Color.Transparent,
                    center = Offset(w * 1.05f, h * 1.1f),
                    radius = big * 0.7f,
                ),
            )
        }
        onDrawBehind {
            drawRect(background)
            for (layer in layers) drawRect(layer)
        }
    }
}

/**
 * The theme plus a background under everything, which gives every screen the look's backdrop and
 * text colours. Without it, text that doesn't set its own colour is black, even in the dark looks.
 * It also makes pen taps on text boxes open the keyboard (see [PenTapsOpenTheKeyboard]).
 */
@Composable
fun AppSurface(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    KoLibTheme(choice) {
        PenTapsOpenTheKeyboard {
            Box(Modifier.fillMaxSize().appBackdrop()) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
            }
        }
    }
}
