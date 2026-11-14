package dev.kobolibrary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import dev.kobolibrary.app.ui.components.PenTapsOpenTheKeyboard

// Ink blue and leather on warm paper.
private val Light = lightColorScheme(
    primary = Color(0xFF2F5D8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF4E6543),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0EBC0),
    onTertiaryContainer = Color(0xFF0C2006),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFEDE6DC),
    onSurfaceVariant = Color(0xFF4D463C),
    outline = Color(0xFF7F766B),
    outlineVariant = Color(0xFFD1C6B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2EA),
    surfaceContainer = Color(0xFFF2ECE3),
    surfaceContainerHigh = Color(0xFFECE6DC),
    surfaceContainerHighest = Color(0xFFE6E0D6),
    surfaceBright = Color(0xFFFBF8F3),
    surfaceDim = Color(0xFFDDD8CF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA2C9F7),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF14456F),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFF0BD8E),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A4315),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFB4CFA5),
    onTertiary = Color(0xFF21361A),
    tertiaryContainer = Color(0xFF374D2D),
    onTertiaryContainer = Color(0xFFD0EBC0),
    background = Color(0xFF161412),
    onBackground = Color(0xFFE8E2D9),
    surface = Color(0xFF161412),
    onSurface = Color(0xFFE8E2D9),
    surfaceVariant = Color(0xFF4D463C),
    onSurfaceVariant = Color(0xFFD0C5B8),
    outline = Color(0xFF998F83),
    outlineVariant = Color(0xFF4D463C),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1E1B18),
    surfaceContainer = Color(0xFF221F1C),
    surfaceContainerHigh = Color(0xFF2D2A26),
    surfaceContainerHighest = Color(0xFF383430),
    surfaceBright = Color(0xFF3C3935),
    surfaceDim = Color(0xFF161412),
)

// Dark, on true black: the screen's black parts are switched off on an OLED screen.
private val Oled = Dark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF2E2A25),
    outlineVariant = Color(0xFF3A342D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080807),
    surfaceContainer = Color(0xFF0E0D0C),
    surfaceContainerHigh = Color(0xFF171513),
    surfaceContainerHighest = Color(0xFF211E1B),
    surfaceBright = Color(0xFF2A2724),
    surfaceDim = Color(0xFF000000),
)

// Dark walnut shelves, brass fittings, oxblood leather and parchment.
private val Bookcase = darkColorScheme(
    primary = Color(0xFFE2B76A),
    onPrimary = Color(0xFF3B2700),
    primaryContainer = Color(0xFF5E4314),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFE8A88C),
    onSecondary = Color(0xFF4A1A08),
    secondaryContainer = Color(0xFF6E2F1D),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFA9C79A),
    onTertiary = Color(0xFF16330E),
    tertiaryContainer = Color(0xFF2E4A26),
    onTertiaryContainer = Color(0xFFC5E4B5),
    background = Color(0xFF2B1C13),
    onBackground = Color(0xFFF1E3CC),
    surface = Color(0xFF2B1C13),
    onSurface = Color(0xFFF1E3CC),
    surfaceVariant = Color(0xFF4A3424),
    onSurfaceVariant = Color(0xFFDCC7AA),
    outline = Color(0xFFA68C6F),
    outlineVariant = Color(0xFF5C4431),
    surfaceContainerLowest = Color(0xFF20140D),
    surfaceContainerLow = Color(0xFF352418),
    surfaceContainer = Color(0xFF3B291C),
    surfaceContainerHigh = Color(0xFF463222),
    surfaceContainerHighest = Color(0xFF523B29),
    surfaceBright = Color(0xFF5A4330),
    surfaceDim = Color(0xFF2B1C13),
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

@Composable
fun KoboLibraryTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val scheme = when (choice) {
        ThemeChoice.SYSTEM -> if (isSystemInDarkTheme()) Dark else Light
        ThemeChoice.LIGHT -> Light
        ThemeChoice.DARK -> Dark
        ThemeChoice.OLED -> Oled
        ThemeChoice.BOOKCASE -> Bookcase
    }
    CompositionLocalProvider(LocalBookcase provides (choice == ThemeChoice.BOOKCASE)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * The theme plus a surface under everything, which gives every screen the theme's background and
 * text colours. Without it, text that doesn't set its own colour is black, even in the dark looks.
 * It also makes pen taps on text boxes open the keyboard (see [PenTapsOpenTheKeyboard]).
 */
@Composable
fun AppSurface(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    KoboLibraryTheme(choice) {
        PenTapsOpenTheKeyboard {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}
