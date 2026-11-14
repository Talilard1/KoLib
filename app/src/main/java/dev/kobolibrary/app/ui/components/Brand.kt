package dev.kobolibrary.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.ui.theme.KoLibGold
import dev.kobolibrary.app.ui.theme.LocalKoLibStyle
import kotlin.math.min

/** The mark's lines, on a 64 × 64 square, as in the opening and the app's icon. */
private class MarkPaths {
    val book: Path = path("M32,17 C25,11.5 15,10.5 6,12.5 V50 C15,48 25,49 32,54.5 M32,17 C39,11.5 49,10.5 58,12.5 V50 C49,48 39,49 32,54.5")
    val ribbon: Path = path("M44,12 V27 L47.5,24 L51,27 V11.6")

    private fun path(d: String) = PathParser().parsePathString(d).toPath()
}

/**
 * KoLib's mark, the open book with its ribbon, in the look's gold. In the dark looks it can [glow]
 * softly, as it does in the opening.
 */
@Composable
fun KoLibMark(modifier: Modifier = Modifier, glow: Boolean = false) {
    val style = LocalKoLibStyle.current
    val paths = remember { MarkPaths() }
    Canvas(modifier) {
        val s = min(size.width, size.height) / 64f
        val dx = (size.width - 64f * s) / 2f
        val dy = (size.height - 64f * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            val gold = Brush.linearGradient(style.markGold, start = Offset(10f, 9f), end = Offset(52f, 58f))
            if (glow && style.dark) {
                for ((width, alpha) in listOf(10f to 0.05f, 6.5f to 0.09f, 4.2f to 0.14f)) {
                    drawPath(paths.book, gold, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(paths.ribbon, gold, alpha = alpha, style = Stroke(width * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            drawPath(paths.book, gold, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(paths.ribbon, gold, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Gold, as on the main buttons: the look's gold from light to deep, a light catching its top edge, a
 * soft shadow under it. For a thing whose own background is clear.
 */
@Composable
fun Modifier.goldFill(shape: Shape, elevation: Dp = 4.dp): Modifier {
    val style = LocalKoLibStyle.current
    return this
        .shadow(elevation, shape, clip = false, ambientColor = style.shadow, spotColor = style.shadow)
        .background(style.buttonBrush(), shape)
        .border(
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    0f to KoLibGold.spark.copy(alpha = 0.85f),
                    0.45f to Color.Transparent,
                    1f to KoLibGold.deep.copy(alpha = 0.55f),
                ),
            ),
            shape,
        )
}

/** The main thing to do on a screen (Read, Add books), in gold. Otherwise just like a [Button]. */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalKoLibStyle.current
    val shape = CircleShape
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = style.onGold,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = contentPadding,
        modifier = if (enabled) modifier.goldFill(shape, elevation = if (style.dark) 6.dp else 3.dp) else modifier,
        content = content,
    )
}

/** A floating button in gold, which can shrink to its icon (as the phone's Add books does). */
@Composable
fun GoldFab(text: String, icon: ImageVector, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalKoLibStyle.current
    val shape = CircleShape
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        // The label isn't read out by itself (it hides when shrunk), so the icon carries the name.
        icon = { Icon(icon, text) },
        text = { Text(text) },
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = style.onGold,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.goldFill(shape, elevation = 8.dp),
    )
}

/** A series' name and number, under a book's title: Garamond, in italic. */
@Composable
fun SeriesStyle(): androidx.compose.ui.text.TextStyle =
    MaterialTheme.typography.titleMedium.copy(
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    )
