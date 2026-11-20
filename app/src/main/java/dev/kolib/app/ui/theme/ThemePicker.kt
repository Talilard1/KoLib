package dev.kolib.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The looks to choose from: each a small picture of itself (its page, a row of covers, its lettering and its
 * main button, in its own colours), with its name under it and what it's like said of the one chosen.
 */
@Composable
fun ThemePicker(current: ThemeChoice, onPick: (ThemeChoice) -> Unit, modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + 10.dp) / 104.dp).toInt().coerceIn(2, 5)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeChoice.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { choice ->
                            val chosen = choice == current
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(choice) })
                                    .padding(3.dp)
                                    .testTag("look-${choice.name}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.18f)
                                        .border(
                                            if (chosen) 2.5.dp else 1.dp,
                                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .padding(if (chosen) 4.5.dp else 1.dp)
                                        .clip(RoundedCornerShape(if (chosen) 8.dp else 11.dp)),
                                ) {
                                    if (choice == ThemeChoice.SYSTEM) {
                                        // Light or dark, as the device is set: half of each.
                                        LookPicture(lookOf(ThemeChoice.LIGHT, false), Modifier.fillMaxSize())
                                        LookPicture(lookOf(ThemeChoice.DARK, true), Modifier.fillMaxSize(), half = true)
                                    } else {
                                        LookPicture(lookOf(choice, systemDark), Modifier.fillMaxSize())
                                    }
                                    if (chosen) {
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape)
                                                .drawBehind { drawRect(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)))) },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Filled.Check, null, Modifier.size(13.dp), tint = androidx.compose.ui.graphics.Color.White) }
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    choice.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        }
                        // A short last row keeps its pictures the size of the others.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(current.label, style = MaterialTheme.typography.titleSmall)
        Text(current.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A look in miniature: its page with its light on it, a title in its ink, three covers (on a shelf, in a
 * bookcase look), a line or two of text, and its main button. With [half], only what's below the diagonal.
 */
@Composable
private fun LookPicture(look: Look, modifier: Modifier, half: Boolean = false) {
    val c = look.scheme
    val style = look.style
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val draw: DrawScope.() -> Unit = {
            drawRect(c.background)
            // The look's light: where its backdrop is brightest.
            val light = when (style.backdrop) {
                Backdrop.BLACK, Backdrop.FLAT -> null
                Backdrop.PAPER, Backdrop.BLUSH, Backdrop.PARCHMENT -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
                else -> style.markGold[1].copy(alpha = 0.22f)
            }
            if (light != null) drawRect(Brush.radialGradient(listOf(light, androidx.compose.ui.graphics.Color.Transparent), center = Offset(w * 0.2f, -h * 0.1f), radius = w * 0.95f))
            when (style.backdrop) {
                Backdrop.MESH -> {
                    // The mesh, much larger than it is, to be seen this small.
                    val side = w / 5f
                    val rise = side * 0.866f
                    var y = 0f
                    while (y < h) { drawRect(style.gold, Offset(0f, y), Size(w, 1f), alpha = 0.12f); y += rise }
                    var x = -h
                    while (x < w + h) {
                        drawLine(style.gold, Offset(x, 0f), Offset(x + h * 0.577f, h), alpha = 0.12f)
                        drawLine(style.gold, Offset(x, 0f), Offset(x - h * 0.577f, h), alpha = 0.12f)
                        x += side
                    }
                }
                Backdrop.SCANLINES -> {
                    var y = 0f
                    while (y < h) { drawRect(style.gold, Offset(0f, y), Size(w, 1f), alpha = 0.07f); y += h / 22f }
                }
                Backdrop.HULL -> {
                    // The hull's panels, much larger than they are, to be seen this small.
                    val across = w / 2.6f
                    val down = across * 0.6f
                    var row = 0
                    var y = 0f
                    while (y < h) {
                        var x = if (row % 2 == 0) 0f else -across / 2
                        while (x < w) { drawRect(androidx.compose.ui.graphics.Color(0xFF9DB7E0), Offset(x + 1.5f, y + 1.5f), Size(across - 3f, down - 3f), alpha = 0.14f, style = androidx.compose.ui.graphics.drawscope.Stroke(1f)); x += across }
                        y += down
                        row++
                    }
                }
                else -> Unit
            }
            val unit = w / 100f
            val pad = 10 * unit
            // The title, and the mark's colours beside it.
            drawCircle(Brush.linearGradient(style.markGold, start = Offset(pad, pad), end = Offset(pad + 9 * unit, pad + 9 * unit)), radius = 4.2f * unit, center = Offset(pad + 4.2f * unit, pad + 5 * unit))
            drawRoundRect(c.onBackground, Offset(pad + 12 * unit, pad + 2.4f * unit), Size(34 * unit, 5.2f * unit), CornerRadius(2.6f * unit), alpha = 0.9f)
            // Three covers.
            val top = pad + 16 * unit
            val coverW = 17 * unit
            val coverH = 25 * unit
            val covers = listOf(c.primaryContainer, c.secondaryContainer, c.tertiaryContainer)
            if (style.shelf != null) drawRect(style.shelf.back, Offset(0f, top - 3 * unit), Size(w, coverH + 9 * unit))
            covers.forEachIndexed { i, colour ->
                val x = pad + i * (coverW + 5 * unit)
                drawRoundRect(colour, Offset(x, top), Size(coverW, coverH), CornerRadius(1.6f * unit))
                drawRoundRect(c.outline, Offset(x, top), Size(coverW, coverH), CornerRadius(1.6f * unit), alpha = 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f * unit))
            }
            if (style.shelf != null) {
                drawRect(Brush.verticalGradient(listOf(style.shelf.plankTop, style.shelf.plankBottom), startY = top + coverH, endY = top + coverH + 4 * unit), Offset(0f, top + coverH), Size(w, 4 * unit))
                drawRect(style.shelf.plankEdge, Offset(0f, top + coverH), Size(w, 0.6f * unit))
            }
            // Text under them, and the main button.
            val text = top + coverH + 10 * unit
            drawRoundRect(c.onSurfaceVariant, Offset(pad, text), Size(40 * unit, 3.6f * unit), CornerRadius(1.8f * unit), alpha = 0.75f)
            drawRoundRect(c.onSurfaceVariant, Offset(pad, text + 7 * unit), Size(27 * unit, 3.6f * unit), CornerRadius(1.8f * unit), alpha = 0.5f)
            val buttonW = 27 * unit
            val buttonH = 10.5f * unit
            val outline = style.actionShape.createOutline(Size(buttonW, buttonH), layoutDirection, this)
            translate(w - pad - buttonW, text - 1.5f * unit) {
                drawOutline(outline, style.gold)
                drawRoundRect(style.onGold, Offset(buttonW * 0.26f, buttonH / 2 - 1.5f * unit), Size(buttonW * 0.48f, 3 * unit), CornerRadius(1.5f * unit), alpha = 0.85f)
            }
        }
        if (half) {
            val below = Path().apply { moveTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() }
            clipPath(below) { draw() }
        } else draw()
    }
}
