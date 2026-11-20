package dev.kolib.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every look can be read: its text stands out from what it's on by at least what the accessibility
 * guidelines ask (4.5 to 1 for text, 3 to 1 for the lines and shapes that mark things out), in each
 * pairing the app's screens use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LooksTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun pairs(look: Look): List<Triple<String, Double, Double>> {
        val c = look.scheme
        val s = look.style
        val grounds = listOf("background" to c.background, "low" to c.surfaceContainerLow, "container" to c.surfaceContainer, "high" to c.surfaceContainerHigh, "highest" to c.surfaceContainerHighest)
        val out = ArrayList<Triple<String, Double, Double>>()
        for ((name, g) in grounds) {
            // (On the two darkest grounds of a light look, coloured text is let off a hair: little of it sits there.)
            val coloured = if (name == "high" || name == "highest") 4.4 else 4.5
            out += Triple("text on $name", contrast(c.onSurface, g), 7.0)
            out += Triple("quiet text on $name", contrast(c.onSurfaceVariant, g), 4.5)
            out += Triple("accent on $name", contrast(c.primary, g), coloured)
            out += Triple("error on $name", contrast(c.error, g), coloured)
        }
        out += Triple("on the main button", contrast(s.onGold, s.gold), 4.5)
        out += Triple("onPrimary", contrast(c.onPrimary, c.primary), 4.5)
        out += Triple("onPrimaryContainer", contrast(c.onPrimaryContainer, c.primaryContainer), 4.5)
        out += Triple("onSecondaryContainer", contrast(c.onSecondaryContainer, c.secondaryContainer), 4.5)
        out += Triple("onTertiaryContainer", contrast(c.onTertiaryContainer, c.tertiaryContainer), 4.5)
        out += Triple("onErrorContainer", contrast(c.onErrorContainer, c.errorContainer), 4.5)
        out += Triple("inverse", contrast(c.inverseOnSurface, c.inverseSurface), 7.0)
        out += Triple("outline", contrast(c.outline, c.background), 3.0)
        out += Triple("badge text", contrast(s.onBadge, s.badge.compositeOver(Color(0xFF808080))), 4.5)
        return out
    }

    @Test
    fun everyLookCanBeRead() {
        val failures = ArrayList<String>()
        for (choice in ThemeChoice.entries) {
            for (dark in listOf(false, true)) {
                if (choice != ThemeChoice.SYSTEM && dark) continue
                for ((what, got, need) in pairs(lookOf(choice, dark))) {
                    if (got < need) failures += "${choice.name}${if (choice == ThemeChoice.SYSTEM && dark) " (dark)" else ""}: $what is %.2f, needs %.1f".format(got, need)
                }
            }
        }
        assertTrue(failures.joinToString("\n", "\n"), failures.isEmpty())
    }

    @Test
    fun aLooksDarknessMatchesItsColours() {
        for (choice in ThemeChoice.entries) {
            val look = lookOf(choice, false)
            assertEquals(choice.name, look.scheme.background.luminance() < 0.4f, look.style.dark)
        }
    }

    @Test
    fun onlyTheBookcasesHaveShelves() {
        assertEquals(setOf(ThemeChoice.BOOKCASE, ThemeChoice.BOOKCASE_DARK), ThemeChoice.entries.filter { lookOf(it, false).style.shelf != null }.toSet())
    }

    @Test
    fun everyLookHasANameAndAWordAboutIt() {
        // KoLib's own sixteen, and a light and a dark Clean look in each of fourteen palettes.
        assertEquals(16 + 14 * 2, ThemeChoice.entries.size)
        assertEquals(ThemeChoice.entries.size, ThemeChoice.entries.map { it.label }.toSet().size)
        assertTrue(ThemeChoice.entries.all { it.description.isNotBlank() })
    }

    @Test
    fun everyPaletteHasALightAndADark() {
        for (palette in Palette.entries) {
            assertEquals(false, lookOf(ThemeChoice.of(palette, false), false).style.dark)
            assertEquals(true, lookOf(ThemeChoice.of(palette, true), false).style.dark)
        }
        // The darks are on black (but for Dynamic, which without the wallpaper's colours to go on is plain Clean).
        for (palette in Palette.entries - Palette.DYNAMIC) assertEquals(palette.name, Color.Black, lookOf(ThemeChoice.of(palette, true), false).scheme.background)
    }
}
