package dev.kolib.app.ui.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kolib.app.reader.ReaderFont
import dev.kolib.app.reader.ReaderTheme
import dev.kolib.app.tr
import dev.kolib.app.ui.components.FittedText
import dev.kolib.app.ui.theme.KoLibGold
import kotlin.math.roundToInt

/** The setup card's colours: light over a light page, dark over a dark one. */
internal class SetupColours(
    val card: Color,
    val text: Color,
    val soft: Color,
    /** Behind a group of choices, and an unchosen tile. */
    val fill: Color,
    /** The chosen one of a group of choices side by side. */
    val pill: Color,
    val line: Color,
    /** Round what's chosen. */
    val ring: Color,
    val button: Color,
    val onButton: Color,
) {
    companion object {
        val Light = SetupColours(
            card = Color.White, text = Color(0xFF1D1B19), soft = Color(0xFF6F6A64), fill = Color(0xFFF1EFEC), pill = Color.White,
            line = Color(0x1F000000), ring = KoLibGold.deep, button = KoLibGold.gold, onButton = KoLibGold.ink,
        )
        val Dark = SetupColours(
            card = Color(0xFF1F1E1D), text = Color(0xFFF3F0EA), soft = Color(0xFFA8A29A), fill = Color(0xFF302E2C), pill = Color(0xFF5C5853),
            line = Color(0x29FFFFFF), ring = KoLibGold.gold, button = KoLibGold.gold, onButton = KoLibGold.ink,
        )
        fun over(page: ReaderTheme): SetupColours = if (page.dark) Dark else Light
    }
}

/** These colours, each fading to the next's when they change (the page going from light to dark, or back). */
@Composable
internal fun SetupColours.fading(): SetupColours {
    @Composable
    fun fade(to: Color, label: String): Color = animateColorAsState(to, tween(260), label = label).value
    return SetupColours(
        card = fade(card, "card"), text = fade(text, "text"), soft = fade(soft, "soft"), fill = fade(fill, "fill"), pill = fade(pill, "pill"),
        line = fade(line, "line"), ring = fade(ring, "ring"), button = fade(button, "button"), onButton = fade(onButton, "onButton"),
    )
}

internal val SetupBody = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
internal val SetupLabel = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
internal val SetupSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)

/** The setup's main button: wide, with the one thing to do next. */
@Composable
internal fun SetupButton(text: String, c: SetupColours, modifier: Modifier = Modifier, height: Dp = 52.dp, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(c.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.onButton, style = TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Which step this is, as dots: the current one long. */
@Composable
internal fun SetupDots(count: Int, current: Int, c: SetupColours, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 18.dp else 6.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "dot")
            val colour by animateColorAsState(if (i == current) c.ring else c.soft.copy(alpha = 0.35f), label = "dotColour")
            Box(Modifier.width(w).height(6.dp).clip(CircleShape).background(colour))
        }
    }
}

/**
 * The top row of a guide's card (the reading setup's, a tour's): back to the step before, which step this is,
 * and leaving the rest for later. Either button is left out when there's nothing for it to do.
 */
@Composable
internal fun GuideHeader(count: Int, at: Int, c: SetupColours, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 12.dp).height(40.dp)) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(34.dp).clip(CircleShape).background(c.fill)
                    .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = tr("Back") }.testTag("guide-back"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = c.text, modifier = Modifier.size(24.dp)) }
        }
        SetupDots(count, at, c, Modifier.align(Alignment.Center))
        if (onSkip != null) {
            Text(
                tr("Skip"), color = c.soft, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onSkip).padding(horizontal = 12.dp, vertical = 8.dp).testTag("guide-skip"),
            )
        }
    }
}

/** The page colours, each a round sample of its paper and ink. */
@Composable
internal fun ThemeSwatches(selected: ReaderTheme, c: SetupColours, onPick: (ReaderTheme) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        ReaderTheme.entries.forEach { t ->
            val chosen = t == selected
            val ring by animateDpAsState(if (chosen) 2.5.dp else 0.dp, label = "ring")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(selected = chosen, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(t)
                    }
                    .padding(vertical = 4.dp)
                    .testTag("theme-${t.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The ring sits a little way out from the sample, so it reads on any colour.
                Box(Modifier.size(54.dp).border(ring, if (chosen) c.ring else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Color(t.bg)).border(1.dp, c.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa", color = Color(t.fg), style = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif), modifier = Modifier.clearAndSetSemantics { })
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    t.label, style = SetupSmall.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (chosen) c.text else c.soft, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false,
                )
            }
        }
    }
}

/** The fonts in the order the setup offers them: the reader's own, then the book's. */
internal val SetupFonts = listOf(
    ReaderFont.LITERATA, ReaderFont.GARAMOND, ReaderFont.SERIF, ReaderFont.SANS, ReaderFont.HYPERLEGIBLE, ReaderFont.DYSLEXIC, ReaderFont.BOOK,
)

/** The fonts, each a tile with "Aa" set in it, in a row that scrolls sideways out to the card's edges. */
@Composable
internal fun FontTiles(selected: ReaderFont, c: SetupColours, edge: Dp, onPick: (ReaderFont) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (SetupFonts.indexOf(selected) - 1).coerceAtLeast(0))
    // The chosen one is brought into view if it's off the side.
    LaunchedEffect(selected) {
        val at = SetupFonts.indexOf(selected)
        val shown = list.layoutInfo.visibleItemsInfo
        val whole = shown.any { it.index == at && it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset - list.layoutInfo.afterContentPadding }
        if (at >= 0 && shown.isNotEmpty() && !whole) list.animateScrollToItem((at - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(SetupFonts, key = { _, f -> f.name }) { _, f ->
            val chosen = f == selected
            val ring by animateDpAsState(if (chosen) 2.5.dp else 0.dp, label = "ring")
            Column(
                Modifier
                    .width(86.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.fill)
                    .border(ring, if (chosen) c.ring else Color.Transparent, RoundedCornerShape(18.dp))
                    .selectable(selected = chosen, role = Role.RadioButton) {
                        if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(f)
                    }
                    .padding(horizontal = 6.dp)
                    .testTag("font-${f.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    val sample = FontSamples.of(f)
                    when {
                        sample != null -> FontSampleLetters(sample, c.text, Modifier.fillMaxSize())
                        f == ReaderFont.BOOK -> Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = c.text, modifier = Modifier.size(28.dp))
                        else -> {
                            // The device's own serif and sans-serif, as the reader uses them: at the samples' size, whatever the device's text size.
                            val size = with(LocalDensity.current) { 29.dp.toSp() }
                            Text(
                                "Aa", color = c.text, maxLines = 1,
                                style = TextStyle(fontSize = size, fontFamily = if (f == ReaderFont.SERIF) FontFamily.Serif else FontFamily.SansSerif),
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    f.label, style = SetupSmall.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (chosen) c.text else c.soft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A slider with a soft track and a round white handle, for a choice among [count] steps; a small and a
 * large "A" at its ends when it sets a size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepSlider(at: Int, count: Int, c: SetupColours, description: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Slider(
        value = at.toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(0, count - 1)
            if (next != at) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(next)
            }
        },
        valueRange = 0f..(count - 1).toFloat(),
        steps = (count - 2).coerceAtLeast(0),
        modifier = modifier.semantics { contentDescription = description }.testTag("size-slider"),
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(4.dp, CircleShape, clip = false)
                    .background(Color.White, CircleShape)
                    .border(0.5.dp, Color(0x26000000), CircleShape),
            )
        },
        track = { state ->
            val fraction = if (count > 1) (state.value / (count - 1)).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(c.fill, size = size, cornerRadius = r)
                drawRoundRect(c.ring, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
        },
    )
}

/** One of a few choices side by side, the chosen one raised: [content] draws each. */
@Composable
internal fun <T> Segments(
    options: List<T>,
    selected: T?,
    c: SetupColours,
    tag: String,
    describe: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    onPick: (T) -> Unit,
    content: @Composable RowScope.(option: T, chosen: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(13.dp)).background(c.fill).padding(3.dp)) {
        val each = maxWidth / options.size
        val at = options.indexOf(selected)
        // The raised piece slides from one choice to the next.
        if (at >= 0) {
            val x by animateDpAsState(each * at, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "segment")
            Box(
                Modifier
                    .offset(x = x)
                    .width(each)
                    .fillMaxHeight()
                    .shadow(2.dp, RoundedCornerShape(10.dp), clip = false)
                    .background(c.pill, RoundedCornerShape(10.dp)),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEach { o ->
                val chosen = o == selected
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(selected = chosen, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!chosen) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPick(o)
                        }
                        .semantics { contentDescription = describe(o) }
                        .testTag("$tag-${describe(o)}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { content(o, chosen) }
            }
        }
    }
}

/** A segment's words: a little stronger when chosen, and made to fit on one line. */
@Composable
internal fun SegmentWords(text: String, chosen: Boolean, c: SetupColours) {
    val colour by animateColorAsState(if (chosen) c.text else c.soft, label = "segmentWords")
    FittedText(
        text, color = colour, textAlign = TextAlign.Center, smallest = 0.7f,
        style = SetupLabel.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
        modifier = Modifier.padding(horizontal = 4.dp).clearAndSetSemantics { },
    )
}

/** Lines of text as a little picture, [gap] apart (in the picture's 24 units): for the line spacings. */
@Composable
internal fun LinesPicture(gap: Float, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val count = 4
        val top = (24f - gap * (count - 1)) / 2f
        for (i in 0 until count) {
            val y = (top + gap * i) * u
            // The last line is short, as a paragraph's is.
            drawLine(colour, Offset(4f * u, y), Offset((if (i == count - 1) 14f else 20f) * u, y), strokeWidth = 1.7f * u, cap = StrokeCap.Round)
        }
    }
}

/** A page with its text [inset] from the sides (in the picture's 24 units): for the margins. */
@Composable
internal fun MarginsPicture(inset: Float, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRoundRect(colour.copy(alpha = 0.55f), Offset(3.5f * u, 2f * u), Size(17f * u, 20f * u), CornerRadius(2.5f * u), style = Stroke(1.3f * u))
        for (i in 0 until 5) {
            val y = (6.5f + i * 2.75f) * u
            drawLine(colour, Offset((3.5f + inset) * u, y), Offset((20.5f - inset) * u, y), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }
    }
}

/** A picture and what it's for, in a list: the menu's buttons, things worth knowing. */
@Composable
internal fun IconLine(icon: ImageVector, text: String, c: SetupColours, note: String? = null, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = if (note == null) Alignment.CenterVertically else Alignment.Top) {
        Box(Modifier.size(32.dp).background(c.fill, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = c.text, style = SetupBody.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal))
            if (note != null) Text(note, color = c.soft, style = SetupLabel.copy(fontWeight = FontWeight.Normal), modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/**
 * Where to touch the page: a ring in the page's ink that swells and fades, over and over. It takes no
 * touches itself, so they reach the page under it.
 */
@Composable
internal fun TouchHere(ink: Color, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "touchHere")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier.size(88.dp)) {
        ring(ink, t)
        ring(ink, (t + 0.5f) % 1f)
        drawCircle(ink.copy(alpha = 0.16f), radius = size.minDimension * 0.19f)
        drawCircle(ink.copy(alpha = 0.6f), radius = size.minDimension * 0.19f, style = Stroke(1.6.dp.toPx()))
    }
}

private fun DrawScope.ring(ink: Color, t: Float) {
    val r = size.minDimension * (0.19f + 0.31f * t)
    drawCircle(ink.copy(alpha = 0.42f * (1f - t)), radius = r, style = Stroke(1.6.dp.toPx()))
}

/** [shown] eased in and out, for a hint that comes and goes. */
@Composable
internal fun easedIn(shown: Boolean): Float {
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "shown")
    return a
}
