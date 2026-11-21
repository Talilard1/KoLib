package dev.kolib.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** Screens less tall than this (a phone held sideways, mostly) let what's at the top of a page slide away. */
const val SHORT_SCREEN_DP = 500

/** A short screen: what's at the top of a page (its bar, its tabs, its search) takes too much of it to stay put. */
@Composable
fun shortScreen(): Boolean = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP

/**
 * [top] above [content]. With [hides] (on a short screen, unless told otherwise), [top] slides up out of sight as
 * [content] is scrolled down and comes back as soon as it's scrolled up, as a browser's address bar does; a scroll
 * that stops with it half gone settles it shown or hidden, whichever's nearer. Inside another, this one's [top] goes
 * once the outer one's has, and comes back after it.
 */
@Composable
fun HidingTop(
    top: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    hides: Boolean = shortScreen(),
    content: @Composable () -> Unit,
) {
    // While a tour's being given, the top stays where it is, so the tour can show what's in it.
    @Suppress("NAME_SHADOWING") val hides = hides && dev.kolib.app.ui.tour.LocalTourPlaces.current == null
    // How far up the top has gone, in pixels: 0 when it's all there, minus its height when it's gone.
    val offset = remember { mutableFloatStateOf(0f) }
    val height = remember { IntArray(1) }
    // Tops inside each other (the library's, then its search) go by the same last scroll, so they come back together.
    val outer = LocalScrollWay.current
    val way = remember(outer) { outer ?: ScrollWay() }
    val connection = remember(way) { HidingTopConnection(offset, height, way) }
    SideEffect { connection.on = hides }
    LaunchedEffect(hides) { if (!hides) offset.floatValue = 0f }
    Layout(
        contents = listOf({ Box { top() } }, { Box(propagateMinConstraints = true) { CompositionLocalProvider(LocalScrollWay provides way) { content() } } }),
        modifier = modifier.nestedScroll(connection).clipToBounds(),
    ) { (tops, contents), c ->
        val t = tops.first().measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        height[0] = t.height
        val gone = if (hides) offset.floatValue.roundToInt().coerceIn(-t.height, 0) else 0
        if (!c.hasBoundedHeight) {
            val p = contents.first().measure(c.copy(minHeight = 0))
            layout(c.maxWidth, t.height + p.height) {
                p.place(0, t.height)
                t.place(0, 0)
            }
        } else {
            val below = (c.maxHeight - t.height - gone).coerceAtLeast(0)
            val p = contents.first().measure(c.copy(minHeight = below, maxHeight = below))
            layout(c.maxWidth, c.maxHeight) {
                p.place(0, t.height + gone)
                t.place(0, gone)
            }
        }
    }
}

/** Takes up what's scrolled, to move the top out of the way first (or back first), and settles it once a fling's done. */
/** Which way the last scroll went: [back] up the list (which brings tops back), or on down it. */
private class ScrollWay {
    var back = false
}

private val LocalScrollWay = staticCompositionLocalOf<ScrollWay?> { null }

private class HidingTopConnection(
    private val offset: androidx.compose.runtime.MutableFloatState,
    private val height: IntArray,
    private val way: ScrollWay,
) : NestedScrollConnection {
    var on = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Noted even when there's none of it left for this top (a top it's inside took it all); not what's left over
        // from that, a speck of a pixel either way.
        if (kotlin.math.abs(available.y) > 0.5f) way.back = available.y > 0f
        if (!on || height[0] == 0 || available.y == 0f) return Offset.Zero
        val before = offset.floatValue
        val after = (before + available.y).coerceIn(-height[0].toFloat(), 0f)
        offset.floatValue = after
        return Offset(0f, after - before)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val h = height[0].toFloat()
        val now = offset.floatValue
        // The rest of the way the scroll was going: a little scroll back up brings it all back (with any top it's
        // inside), and one left part-way down goes.
        val to = when {
            !on || h <= 0f -> null
            way.back && now < 0f -> 0f
            !way.back && now < 0f && now > -h -> -h
            else -> null
        }
        if (to != null) animate(now, to) { v, _ -> offset.floatValue = v }
        return Velocity.Zero
    }
}
