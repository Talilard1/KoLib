package dev.kolib.app.ui.opening

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.kolib.app.R
import dev.kolib.app.ui.theme.Lettering
import dev.kolib.app.ui.theme.ThemeChoice

/**
 * The opening's colours and lettering in one of the app's looks, so KoLib opens in the look chosen: the dark it
 * starts from, the glow in it, the light the threads, the mark and the sparks are drawn in, and the name's
 * colours and type. Every look's is dark, so the light shows up on it.
 */
internal class OpeningLook(
    val night: Color,
    val glow: Color,
    val ember: Color,
    val goldLight: Color,
    val gold: Color,
    val goldDeep: Color,
    val spark: Color,
    val cream: Color,
    /** The mark's own light, from its top left to its middle (to [goldDeep] at its foot). */
    val markLight: Color,
    val markMid: Color,
    val lettering: Lettering = Lettering.GARAMOND,
) {
    companion object {
        /** Gold on dark brown: KoLib's own, the Light and Dark looks'. */
        val Gold = OpeningLook(
            night = Color(0xFF1E120B), glow = Color(0xFF46291A), ember = Color(0xFF7A4A22),
            goldLight = Color(0xFFF6CF95), gold = Color(0xFFE2B76A), goldDeep = Color(0xFFC98A45),
            spark = Color(0xFFFFF3D6), cream = Color(0xFFF6EFE4), markLight = Color(0xFFFBE6BF), markMid = Color(0xFFEBC07E),
        )
        private val Oled = OpeningLook(
            night = Color(0xFF000000), glow = Color(0xFF2E1C10), ember = Color(0xFF61401E),
            goldLight = Gold.goldLight, gold = Gold.gold, goldDeep = Gold.goldDeep, spark = Gold.spark, cream = Gold.cream,
            markLight = Gold.markLight, markMid = Gold.markMid,
        )
        /** Brass on dark wood. */
        private val Bookcase = OpeningLook(
            night = Color(0xFF170D06), glow = Color(0xFF4A2A14), ember = Color(0xFF8C5524),
            goldLight = Color(0xFFF2CE8E), gold = Color(0xFFD9A95C), goldDeep = Color(0xFFAD7A36),
            spark = Color(0xFFFFF1D2), cream = Color(0xFFF5EBD9), markLight = Color(0xFFF8E2B2), markMid = Color(0xFFE6BB72),
        )
        /** Blue on graphite. */
        private val Clean = OpeningLook(
            night = Color(0xFF0A0D14), glow = Color(0xFF15233D), ember = Color(0xFF1D3D73),
            goldLight = Color(0xFFA9C8FF), gold = Color(0xFF5B9CFF), goldDeep = Color(0xFF2F6FD6),
            spark = Color(0xFFEEF4FF), cream = Color(0xFFF2F5FA), markLight = Color(0xFFD6E4FF), markMid = Color(0xFF8AB6FF),
            lettering = Lettering.SANS,
        )
        /** Gold leaf on oxblood. */
        private val Manuscript = OpeningLook(
            night = Color(0xFF1C0806), glow = Color(0xFF4A1410), ember = Color(0xFF7E231A),
            goldLight = Color(0xFFF3D08F), gold = Color(0xFFDDB15E), goldDeep = Color(0xFFB8863A),
            spark = Color(0xFFFFF4DA), cream = Color(0xFFF6EAD0), markLight = Color(0xFFF9E3B0), markMid = Color(0xFFE8BF70),
        )
        /** Blush and rose on wine. */
        private val Romance = OpeningLook(
            night = Color(0xFF1E0710), glow = Color(0xFF4C1428), ember = Color(0xFF7E2546),
            goldLight = Color(0xFFFFC9D6), gold = Color(0xFFF08DA8), goldDeep = Color(0xFFC2507A),
            spark = Color(0xFFFFF0F4), cream = Color(0xFFFFF3F5), markLight = Color(0xFFFFD9E2), markMid = Color(0xFFF6A9BD),
            lettering = Lettering.GARAMOND_ITALIC,
        )
        /** Red on black, as the lights go down. */
        private val Cinema = OpeningLook(
            night = Color(0xFF080506), glow = Color(0xFF3A0A0E), ember = Color(0xFF6E1016),
            goldLight = Color(0xFFFF9EA2), gold = Color(0xFFFF5F66), goldDeep = Color(0xFFC8323A),
            spark = Color(0xFFFFE9EA), cream = Color(0xFFFFF2F2), markLight = Color(0xFFFFC9CB), markMid = Color(0xFFFF8A90),
            lettering = Lettering.SANS_HEAVY,
        )
        /** Green on black. */
        private val Terminal = OpeningLook(
            night = Color(0xFF020604), glow = Color(0xFF0A2A18), ember = Color(0xFF0E4D2A),
            goldLight = Color(0xFF9CFFC2), gold = Color(0xFF3DFF8B), goldDeep = Color(0xFF17B85A),
            spark = Color(0xFFE6FFEF), cream = Color(0xFFD9FFE6), markLight = Color(0xFFC2FFD8), markMid = Color(0xFF6BFFA6),
            lettering = Lettering.MONO,
        )
        /** Ice blue and teal on a ship's hull at night, with amber lights. */
        private val Starship = OpeningLook(
            night = Color(0xFF070B13), glow = Color(0xFF17263A), ember = Color(0xFF5A3F16),
            goldLight = Color(0xFFCFE6FF), gold = Color(0xFF8FC2FF), goldDeep = Color(0xFF2FA5A0),
            spark = Color(0xFFFFE2AE), cream = Color(0xFFEEF4FC), markLight = Color(0xFFDCEBFF), markMid = Color(0xFFA6CFFF),
            lettering = Lettering.ORBITRON,
        )
        /** Pale cyan light in the dark. */
        private val Hologram = OpeningLook(
            night = Color(0xFF02070F), glow = Color(0xFF082A3A), ember = Color(0xFF0B4558),
            goldLight = Color(0xFFC9F4FF), gold = Color(0xFF6FE0FF), goldDeep = Color(0xFF2BA8C8),
            spark = Color(0xFFECFDFF), cream = Color(0xFFE4FAFF), markLight = Color(0xFFD2F6FF), markMid = Color(0xFF9AEBFF),
            lettering = Lettering.AIRY,
        )
        /** Ember orange on gunmetal. */
        private val Relic = OpeningLook(
            night = Color(0xFF0C0E0D), glow = Color(0xFF2A1A10), ember = Color(0xFF6A2E0A),
            goldLight = Color(0xFFFFC08F), gold = Color(0xFFFF8F45), goldDeep = Color(0xFFC85E1C),
            spark = Color(0xFFFFEBDC), cream = Color(0xFFF3EEE4), markLight = Color(0xFFFFD3AF), markMid = Color(0xFFFFA766),
            lettering = Lettering.CONSOLE,
        )

        fun of(choice: ThemeChoice): OpeningLook = when (choice) {
            ThemeChoice.SYSTEM, ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.BOOKCASE_DARK -> Gold
            ThemeChoice.OLED -> Oled
            ThemeChoice.BOOKCASE -> Bookcase
            ThemeChoice.CLEAN_LIGHT, ThemeChoice.CLEAN_DARK -> Clean
            ThemeChoice.MANUSCRIPT -> Manuscript
            ThemeChoice.ROMANCE -> Romance
            ThemeChoice.CINEMA -> Cinema
            ThemeChoice.TERMINAL -> Terminal
            ThemeChoice.STARSHIP -> Starship
            ThemeChoice.HOLOGRAM -> Hologram
            ThemeChoice.RELIC -> Relic
        }
    }
}

/** The look the opening is drawn in: set as it starts, before anything of it is made (one plays at a time). */
internal object OpeningColours {
    @Volatile var look: OpeningLook = OpeningLook.Gold
}

/** How the name is lettered in the opening: its type, how far apart its letters are (in ems), and its size beside the usual. */
internal class NameFont(val family: FontFamily, val weight: FontWeight?, val spacing: Float, val scale: Float)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal fun nameFont(lettering: Lettering): NameFont = when (lettering) {
    Lettering.GARAMOND -> NameFont(FontFamily(Font(R.font.eb_garamond_kolib)), null, 0.02f, 1f)
    Lettering.GARAMOND_ITALIC -> NameFont(FontFamily(Font(R.font.eb_garamond_ui_medium_italic)), null, 0.02f, 1f)
    Lettering.SANS -> NameFont(FontFamily.SansSerif, FontWeight.SemiBold, -0.01f, 0.84f)
    Lettering.SANS_HEAVY -> NameFont(FontFamily.SansSerif, FontWeight.Black, 0f, 0.84f)
    Lettering.MONO -> NameFont(FontFamily.Monospace, FontWeight.Bold, 0f, 0.78f)
    Lettering.CONSOLE -> NameFont(
        runCatching { FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)) }.getOrDefault(FontFamily.SansSerif),
        FontWeight.Medium, 0.08f, 0.9f,
    )
    Lettering.ORBITRON -> NameFont(
        FontFamily(Font(R.font.orbitron_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))),
        FontWeight.SemiBold, 0.04f, 0.7f,
    )
    Lettering.AIRY -> NameFont(FontFamily.SansSerif, FontWeight.Light, 0.12f, 0.86f)
}
