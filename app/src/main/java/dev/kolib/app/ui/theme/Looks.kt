package dev.kolib.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Everything a look is made of: its colours, what the colours can't say, its lettering and its corners. */
@Immutable
class Look(val scheme: ColorScheme, val style: KoLibStyle, val typography: Typography = KoLibTypography, val shapes: Shapes = Shapes())

/** The look [choice] stands for ([systemDark]: the device is set to dark, for "System default"). */
fun lookOf(choice: ThemeChoice, systemDark: Boolean): Look = when (choice) {
    ThemeChoice.SYSTEM -> if (systemDark) DarkLook else LightLook
    ThemeChoice.LIGHT -> LightLook
    ThemeChoice.DARK -> DarkLook
    ThemeChoice.OLED -> OledLook
    ThemeChoice.BOOKCASE -> BookcaseLook
    ThemeChoice.BOOKCASE_DARK -> BookcaseDarkLook
    ThemeChoice.CLEAN_LIGHT -> CleanLightLook
    ThemeChoice.CLEAN_DARK -> CleanDarkLook
    ThemeChoice.MANUSCRIPT -> ManuscriptLook
    ThemeChoice.ROMANCE -> RomanceLook
    ThemeChoice.CINEMA -> CinemaLook
    ThemeChoice.TERMINAL -> TerminalLook
    ThemeChoice.STARSHIP -> StarshipLook
    ThemeChoice.HOLOGRAM -> HologramLook
    ThemeChoice.RELIC -> RelicLook
}

private val LightLook = Look(LightScheme, LightStyle)
private val DarkLook = Look(DarkScheme, DarkStyle)
private val OledLook = Look(OledScheme, OledStyle)
private val BookcaseLook = Look(BookcaseScheme, BookcaseStyle)

// ---- Bookcase, dark: the Dark look's gold on deep brown, with shelves of darker wood under lamplight ----

private val BookcaseDarkLook = Look(
    DarkScheme,
    DarkStyle.copy(
        backdrop = Backdrop.LAMP,
        shelf = ShelfWood(plankTop = Color(0xFF6E4728), plankBottom = Color(0xFF3B2415), plankEdge = Color(0xFFA9763F), back = Color(0xFF120B07)),
    ),
)

// ---- Clean: flat white or black with one blue, and nothing drawn behind ---------------------------

private fun softCorners(xs: Int, s: Int, m: Int, l: Int, xl: Int) =
    Shapes(RoundedCornerShape(xs.dp), RoundedCornerShape(s.dp), RoundedCornerShape(m.dp), RoundedCornerShape(l.dp), RoundedCornerShape(xl.dp))

private fun cutCorners(xs: Int, s: Int, m: Int, l: Int, xl: Int) =
    Shapes(CutCornerShape(xs.dp), CutCornerShape(s.dp), CutCornerShape(m.dp), CutCornerShape(l.dp), CutCornerShape(xl.dp))

private val CleanLightLook = Look(
    lightColorScheme(
        primary = Color(0xFF0F5BD6), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF05204F),
        secondary = Color(0xFF55606F), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE4E7EE), onSecondaryContainer = Color(0xFF1B2028),
        tertiary = Color(0xFF1B8554), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFD4F2E2), onTertiaryContainer = Color(0xFF06301C),
        error = Color(0xFFC2251A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFDE3E0), onErrorContainer = Color(0xFF4A0D08),
        background = Color(0xFFF3F3F6), onBackground = Color(0xFF111114), surface = Color(0xFFF3F3F6), onSurface = Color(0xFF111114),
        surfaceVariant = Color(0xFFE7E7EC), onSurfaceVariant = Color(0xFF5C5C65), surfaceTint = Color(0xFF1366E8),
        outline = Color(0xFF8A8A93), outlineVariant = Color(0xFFD9D9DF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFAFAFC),
        surfaceContainerHigh = Color(0xFFEDEDF1), surfaceContainerHighest = Color(0xFFE4E4E9), surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFDADADF),
        inverseSurface = Color(0xFF1C1C1F), inverseOnSurface = Color(0xFFF3F3F6), inversePrimary = Color(0xFF8DB6FF),
    ),
    KoLibStyle(
        dark = false, backdrop = Backdrop.FLAT,
        gold = Color(0xFF1366E8), onGold = Color(0xFFFFFFFF),
        markGold = listOf(Color(0xFF5A9BFF), Color(0xFF1366E8), Color(0xFF0B49B0)),
        hairline = Color(0xFF111114).copy(alpha = 0.09f),
        badge = Color(0xFF111114).copy(alpha = 0.62f), onBadge = Color(0xFFFFFFFF),
        shadow = Color(0xFF1A2233),
    ),
    typographyFor(Lettering.SANS),
    softCorners(8, 10, 14, 18, 26),
)

private val CleanDarkLook = Look(
    darkColorScheme(
        primary = Color(0xFF5B9CFF), onPrimary = Color(0xFF001B45), primaryContainer = Color(0xFF123A7A), onPrimaryContainer = Color(0xFFD8E6FF),
        secondary = Color(0xFFB4BAC6), onSecondary = Color(0xFF20242C), secondaryContainer = Color(0xFF2A2E37), onSecondaryContainer = Color(0xFFDEE2EA),
        tertiary = Color(0xFF5FD39A), onTertiary = Color(0xFF00321C), tertiaryContainer = Color(0xFF0F4A2E), onTertiaryContainer = Color(0xFFBFF2D6),
        error = Color(0xFFFF8A80), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF6B1D16), onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF0B0B0D), onBackground = Color(0xFFF2F2F5), surface = Color(0xFF0B0B0D), onSurface = Color(0xFFF2F2F5),
        surfaceVariant = Color(0xFF2C2C31), onSurfaceVariant = Color(0xFFA3A3AC), surfaceTint = Color(0xFF5B9CFF),
        outline = Color(0xFF70707A), outlineVariant = Color(0xFF2E2E34),
        surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF17171A), surfaceContainer = Color(0xFF1D1D21),
        surfaceContainerHigh = Color(0xFF26262B), surfaceContainerHighest = Color(0xFF303036), surfaceBright = Color(0xFF3A3A41), surfaceDim = Color(0xFF0B0B0D),
        inverseSurface = Color(0xFFF2F2F5), inverseOnSurface = Color(0xFF17171A), inversePrimary = Color(0xFF1366E8),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.FLAT,
        gold = Color(0xFF2468E0), onGold = Color(0xFFFFFFFF),
        markGold = listOf(Color(0xFFA9CBFF), Color(0xFF5B9CFF), Color(0xFF2468E0)),
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.10f),
        badge = Color.Black.copy(alpha = 0.66f), onBadge = Color(0xFFFFFFFF),
        shadow = Color.Black,
    ),
    typographyFor(Lettering.SANS),
    softCorners(8, 10, 14, 18, 26),
)

// ---- Manuscript: an illuminated page. Parchment, iron-gall ink, oxblood, lapis and gold leaf ------

private val ManuscriptLook = Look(
    lightColorScheme(
        primary = Color(0xFF8C1D18), onPrimary = Color(0xFFFFF4E0), primaryContainer = Color(0xFFEFC7AC), onPrimaryContainer = Color(0xFF3F0906),
        secondary = Color(0xFF1F4B8E), onSecondary = Color(0xFFF4F0FF), secondaryContainer = Color(0xFFD9D6C4), onSecondaryContainer = Color(0xFF12284F),
        tertiary = Color(0xFF2F6B4A), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFCFDDB8), onTertiaryContainer = Color(0xFF0C2A18),
        error = Color(0xFFA8201A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFF3CFC2), onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF2E6CC), onBackground = Color(0xFF2B1D10), surface = Color(0xFFF2E6CC), onSurface = Color(0xFF2B1D10),
        surfaceVariant = Color(0xFFE2D2B0), onSurfaceVariant = Color(0xFF5E4A30), surfaceTint = Color(0xFF8C1D18),
        outline = Color(0xFF8A7350), outlineVariant = Color(0xFFD1BD95),
        surfaceContainerLowest = Color(0xFFFBF3DF), surfaceContainerLow = Color(0xFFEFE2C5), surfaceContainer = Color(0xFFEADBBB),
        surfaceContainerHigh = Color(0xFFE4D3B0), surfaceContainerHighest = Color(0xFFDDCAA4), surfaceBright = Color(0xFFF7ECD3), surfaceDim = Color(0xFFD8C7A2),
        inverseSurface = Color(0xFF3A2A18), inverseOnSurface = Color(0xFFF6EAD0), inversePrimary = Color(0xFFF2B8A6),
    ),
    KoLibStyle(
        dark = false, backdrop = Backdrop.PARCHMENT,
        gold = Color(0xFFCBA135), onGold = Color(0xFF2A1806),
        markGold = listOf(Color(0xFFC79A2C), Color(0xFF9A3A1C), Color(0xFF6A100C)),
        hairline = Color(0xFF8A6A2A).copy(alpha = 0.36f),
        badge = Color(0xFF2B1D10).copy(alpha = 0.70f), onBadge = Color(0xFFFFF4DC),
        shadow = Color(0xFF4A3212),
        actionShape = RoundedCornerShape(6.dp),
    ),
    typographyFor(Lettering.GARAMOND),
    softCorners(3, 4, 6, 8, 12),
)

// ---- Romance: blush paper, rose and wine, antique gold, and titles in a flowing italic ------------

private val RomanceLook = Look(
    lightColorScheme(
        primary = Color(0xFFA2305A), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFFBD3DF), onPrimaryContainer = Color(0xFF44081F),
        secondary = Color(0xFF8A4A5E), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFF6DCE3), onSecondaryContainer = Color(0xFF3A1220),
        tertiary = Color(0xFF8E6327), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFF6E3C4), onTertiaryContainer = Color(0xFF2E1D05),
        error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFCF2F1), onBackground = Color(0xFF3A1A22), surface = Color(0xFFFCF2F1), onSurface = Color(0xFF3A1A22),
        surfaceVariant = Color(0xFFF3DEDF), onSurfaceVariant = Color(0xFF7A4F58), surfaceTint = Color(0xFFA2305A),
        outline = Color(0xFFA9808A), outlineVariant = Color(0xFFEACDD2),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAEBEA), surfaceContainer = Color(0xFFF7E4E4),
        surfaceContainerHigh = Color(0xFFF2DCDD), surfaceContainerHighest = Color(0xFFEDD4D6), surfaceBright = Color(0xFFFFF8F7), surfaceDim = Color(0xFFE4CACD),
        inverseSurface = Color(0xFF4A2530), inverseOnSurface = Color(0xFFFFEDEF), inversePrimary = Color(0xFFFFB1C8),
    ),
    KoLibStyle(
        dark = false, backdrop = Backdrop.BLUSH,
        gold = Color(0xFFC2456B), onGold = Color(0xFFFFFFFF),
        markGold = listOf(Color(0xFFE58CA6), Color(0xFFC2456B), Color(0xFF7A1F3F)),
        hairline = Color(0xFFB03A62).copy(alpha = 0.20f),
        badge = Color(0xFF3A1A22).copy(alpha = 0.66f), onBadge = Color(0xFFFFF1F3),
        shadow = Color(0xFF6A2A3A),
    ),
    typographyFor(Lettering.GARAMOND_ITALIC),
    softCorners(10, 14, 20, 26, 32),
)

// ---- Cinema: a dark room, a red light from the screen, and heavy plain lettering -------------------

private val CinemaLook = Look(
    darkColorScheme(
        primary = Color(0xFFFF5F66), onPrimary = Color(0xFF2B0004), primaryContainer = Color(0xFF5C0A10), onPrimaryContainer = Color(0xFFFFD9D9),
        secondary = Color(0xFFC9C9CF), onSecondary = Color(0xFF1D1D21), secondaryContainer = Color(0xFF2B2B31), onSecondaryContainer = Color(0xFFE6E6EA),
        tertiary = Color(0xFFF2C14E), onTertiary = Color(0xFF3A2A00), tertiaryContainer = Color(0xFF4D3A0A), onTertiaryContainer = Color(0xFFFFE6A8),
        error = Color(0xFFFFB4A9), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF7A2A1E), onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF0A0A0C), onBackground = Color(0xFFF4F4F5), surface = Color(0xFF0A0A0C), onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF26262B), onSurfaceVariant = Color(0xFFA8A8B0), surfaceTint = Color(0xFFFF5F66),
        outline = Color(0xFF6E6E78), outlineVariant = Color(0xFF2A2A30),
        surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF131316), surfaceContainer = Color(0xFF18181C),
        surfaceContainerHigh = Color(0xFF202025), surfaceContainerHighest = Color(0xFF2A2A30), surfaceBright = Color(0xFF34343B), surfaceDim = Color(0xFF0A0A0C),
        inverseSurface = Color(0xFFF4F4F5), inverseOnSurface = Color(0xFF18181C), inversePrimary = Color(0xFFB3151F),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.SPOTLIGHT,
        gold = Color(0xFFD4202A), onGold = Color(0xFFFFFFFF),
        markGold = listOf(Color(0xFFFF9A9E), Color(0xFFE5303A), Color(0xFF9C0F18)),
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.09f),
        badge = Color.Black.copy(alpha = 0.70f), onBadge = Color(0xFFFFFFFF),
        shadow = Color.Black,
        actionShape = RoundedCornerShape(5.dp),
    ),
    typographyFor(Lettering.SANS_HEAVY),
    softCorners(3, 4, 6, 8, 10),
)

// ---- Terminal: green phosphor on black glass, in fixed-width type ---------------------------------

private val TerminalLook = Look(
    darkColorScheme(
        primary = Color(0xFF3DFF8B), onPrimary = Color(0xFF00210E), primaryContainer = Color(0xFF0B3D22), onPrimaryContainer = Color(0xFF9CFFC2),
        secondary = Color(0xFF7DE0B0), onSecondary = Color(0xFF00382A), secondaryContainer = Color(0xFF0E2E20), onSecondaryContainer = Color(0xFFA9F2CF),
        tertiary = Color(0xFFC6F26B), onTertiary = Color(0xFF1E2B00), tertiaryContainer = Color(0xFF2A3A0A), onTertiaryContainer = Color(0xFFDCF7A0),
        error = Color(0xFFFF8A7A), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF5E1A12), onErrorContainer = Color(0xFFFFD9D2),
        background = Color(0xFF030806), onBackground = Color(0xFFBDF7CF), surface = Color(0xFF030806), onSurface = Color(0xFFBDF7CF),
        surfaceVariant = Color(0xFF12301F), onSurfaceVariant = Color(0xFF74C48F), surfaceTint = Color(0xFF3DFF8B),
        outline = Color(0xFF3F7A57), outlineVariant = Color(0xFF173A26),
        surfaceContainerLowest = Color(0xFF010403), surfaceContainerLow = Color(0xFF07110C), surfaceContainer = Color(0xFF0A1710),
        surfaceContainerHigh = Color(0xFF0E1F16), surfaceContainerHighest = Color(0xFF13291D), surfaceBright = Color(0xFF18331F), surfaceDim = Color(0xFF030806),
        inverseSurface = Color(0xFFBDF7CF), inverseOnSurface = Color(0xFF06140C), inversePrimary = Color(0xFF0A7A3C),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.SCANLINES,
        gold = Color(0xFF3DFF8B), onGold = Color(0xFF00210E),
        markGold = listOf(Color(0xFFB8FFD3), Color(0xFF3DFF8B), Color(0xFF0FA958)),
        hairline = Color(0xFF3DFF8B).copy(alpha = 0.24f),
        badge = Color.Black.copy(alpha = 0.72f), onBadge = Color(0xFFD9FFE6),
        shadow = Color.Black,
        actionShape = RoundedCornerShape(3.dp),
    ),
    typographyFor(Lettering.MONO),
    softCorners(2, 2, 3, 4, 6),
)

// ---- Starship: a ship's hull at night. Steel-blue panels, ice blue, with amber and teal lights ------

private val StarshipLook = Look(
    darkColorScheme(
        primary = Color(0xFF8FC2FF), onPrimary = Color(0xFF04203F), primaryContainer = Color(0xFF12345E), onPrimaryContainer = Color(0xFFD6E8FF),
        secondary = Color(0xFFFFB866), onSecondary = Color(0xFF3A2300), secondaryContainer = Color(0xFF1C2A40), onSecondaryContainer = Color(0xFFDCE7FA),
        tertiary = Color(0xFF6FE3D0), onTertiary = Color(0xFF00352E), tertiaryContainer = Color(0xFF0E3B3A), onTertiaryContainer = Color(0xFFBDF5EC),
        error = Color(0xFFFF8E80), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF6E241A), onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF0A0F18), onBackground = Color(0xFFE8EEFA), surface = Color(0xFF0A0F18), onSurface = Color(0xFFE8EEFA),
        surfaceVariant = Color(0xFF1C2638), onSurfaceVariant = Color(0xFFA6B5CF), surfaceTint = Color(0xFF8FC2FF),
        outline = Color(0xFF63759A), outlineVariant = Color(0xFF212C40),
        surfaceContainerLowest = Color(0xFF060910), surfaceContainerLow = Color(0xFF0E1420), surfaceContainer = Color(0xFF121A28),
        surfaceContainerHigh = Color(0xFF172133), surfaceContainerHighest = Color(0xFF1D2840), surfaceBright = Color(0xFF253353), surfaceDim = Color(0xFF0A0F18),
        inverseSurface = Color(0xFFE8EEFA), inverseOnSurface = Color(0xFF10182A), inversePrimary = Color(0xFF2D63B8),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.HULL,
        gold = Color(0xFF4C9DFF), onGold = Color(0xFF001A3D),
        markGold = listOf(Color(0xFFDCEBFF), Color(0xFF8FC2FF), Color(0xFF2D6FD6)),
        hairline = Color(0xFF8FC2FF).copy(alpha = 0.20f),
        badge = Color(0xFF02040A).copy(alpha = 0.72f), onBadge = Color(0xFFEAF2FF),
        shadow = Color.Black,
        // Like a key on a console: two corners rounder than the others.
        actionShape = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
    ),
    typographyFor(Lettering.ORBITRON),
    softCorners(4, 6, 10, 12, 18),
)

// ---- Hologram: pale blue light in the air, drawn as a fine mesh, cut corners -----------------------

private val HologramLook = Look(
    darkColorScheme(
        primary = Color(0xFF7CCBFF), onPrimary = Color(0xFF00243D), primaryContainer = Color(0xFF0B3554), onPrimaryContainer = Color(0xFFC9E9FF),
        secondary = Color(0xFFA5D8FF), onSecondary = Color(0xFF062740), secondaryContainer = Color(0xFF0A2238), onSecondaryContainer = Color(0xFFCFEBFF),
        tertiary = Color(0xFF78F2E6), onTertiary = Color(0xFF003833), tertiaryContainer = Color(0xFF0A3A3D), onTertiaryContainer = Color(0xFFB8F8F1),
        error = Color(0xFFFF9D8E), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF6E241A), onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF030A14), onBackground = Color(0xFFD4EEFF), surface = Color(0xFF030A14), onSurface = Color(0xFFD4EEFF),
        surfaceVariant = Color(0xFF0D2438), onSurfaceVariant = Color(0xFF8FC2E2), surfaceTint = Color(0xFF7CCBFF),
        outline = Color(0xFF3D84B4), outlineVariant = Color(0xFF13344F),
        surfaceContainerLowest = Color(0xFF01060D), surfaceContainerLow = Color(0xFF061220), surfaceContainer = Color(0xFF08182A),
        surfaceContainerHigh = Color(0xFF0B2034), surfaceContainerHighest = Color(0xFF0F2940), surfaceBright = Color(0xFF143552), surfaceDim = Color(0xFF030A14),
        inverseSurface = Color(0xFFD4EEFF), inverseOnSurface = Color(0xFF06182A), inversePrimary = Color(0xFF1E6FA8),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.MESH,
        gold = Color(0xFF3FAEF0), onGold = Color(0xFF001726),
        markGold = listOf(Color(0xFFE6F7FF), Color(0xFF7CCBFF), Color(0xFF1F8FD6)),
        hairline = Color(0xFF7CCBFF).copy(alpha = 0.30f),
        badge = Color(0xFF000307).copy(alpha = 0.72f), onBadge = Color(0xFFE2F6FF),
        shadow = Color.Black,
        actionShape = CutCornerShape(8.dp),
    ),
    typographyFor(Lettering.AIRY),
    cutCorners(3, 5, 8, 10, 14),
)

// ---- Relic: an old machine still running. Gunmetal plating, ember orange, aged gold and patina -----

private val RelicLook = Look(
    darkColorScheme(
        primary = Color(0xFFFF8F45), onPrimary = Color(0xFF3E1700), primaryContainer = Color(0xFF6A2E0A), onPrimaryContainer = Color(0xFFFFDCC2),
        secondary = Color(0xFFD9B36A), onSecondary = Color(0xFF382600), secondaryContainer = Color(0xFF3D3520), onSecondaryContainer = Color(0xFFF7E0AE),
        tertiary = Color(0xFF6FC7B3), onTertiary = Color(0xFF00382E), tertiaryContainer = Color(0xFF174A40), onTertiaryContainer = Color(0xFFBDEFE3),
        error = Color(0xFFFF9D8E), onError = Color(0xFF4A0C06), errorContainer = Color(0xFF70261B), onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF0F1211), onBackground = Color(0xFFE8E4D9), surface = Color(0xFF0F1211), onSurface = Color(0xFFE8E4D9),
        surfaceVariant = Color(0xFF2A322F), onSurfaceVariant = Color(0xFFAAB2A8), surfaceTint = Color(0xFFFF8F45),
        outline = Color(0xFF6F7A72), outlineVariant = Color(0xFF2B3431),
        surfaceContainerLowest = Color(0xFF090B0A), surfaceContainerLow = Color(0xFF171B1A), surfaceContainer = Color(0xFF1C2120),
        surfaceContainerHigh = Color(0xFF232A28), surfaceContainerHighest = Color(0xFF2C3432), surfaceBright = Color(0xFF37403D), surfaceDim = Color(0xFF0F1211),
        inverseSurface = Color(0xFFE8E4D9), inverseOnSurface = Color(0xFF1C2120), inversePrimary = Color(0xFFA04A12),
    ),
    KoLibStyle(
        dark = true, backdrop = Backdrop.PLATES,
        gold = Color(0xFFFF8F45), onGold = Color(0xFF2E1000),
        markGold = listOf(Color(0xFFFFD9B8), Color(0xFFFF8F45), Color(0xFFC2521A)),
        hairline = Color(0xFFD9B36A).copy(alpha = 0.24f),
        badge = Color(0xFF050606).copy(alpha = 0.72f), onBadge = Color(0xFFFFF1E2),
        shadow = Color.Black,
        actionShape = CutCornerShape(12.dp, 3.dp, 12.dp, 3.dp),
    ),
    typographyFor(Lettering.CONSOLE),
    cutCorners(3, 6, 10, 12, 16),
)
