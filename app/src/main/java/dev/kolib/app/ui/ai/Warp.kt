package dev.kolib.app.ui.ai

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * The AI's window warping into a point (its bubble, or the AI button at the top) and back out of it, like a genie
 * into its lamp: the window's far side bends towards the point, and the rest pours in after it.
 */
object Warp {
    /**
     * The warp, as a shader (AGSL, from Android 13): for each point of the screen, where in the window [win] (left,
     * top, right, bottom) it shows, [progress] of the way (0 to 1) into [target], which is [size] across. [way]: where
     * the target is from the window, 0 below, 1 above, 2 to the right, 3 to the left; the sums are done as if it
     * were below.
     */
    const val SHADER = """
uniform shader content;
uniform float4 win;
uniform float2 target;
uniform float size;
uniform float progress;
uniform float way;

float2 toWay(float2 p) {
    if (way < 0.5) return p;
    if (way < 1.5) return float2(p.x, -p.y);
    if (way < 2.5) return float2(p.y, p.x);
    return float2(p.y, -p.x);
}

float2 fromWay(float2 q) {
    if (way < 0.5) return q;
    if (way < 1.5) return float2(q.x, -q.y);
    if (way < 2.5) return float2(q.y, q.x);
    return float2(-q.y, q.x);
}

half4 main(float2 p) {
    float2 a = toWay(win.xy);
    float2 b = toWay(win.zw);
    float4 w = float4(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y));
    float2 t = toWay(target);
    float2 q = toWay(p);
    float width = w.z - w.x;
    float height = w.w - w.y;
    float cx = (w.x + w.z) * 0.5;
    float bend = smoothstep(0.0, 0.45, progress);
    float fall = smoothstep(0.15, 1.0, progress);
    float top = mix(w.y, t.y, fall * fall);
    float bottom = mix(w.w, t.y, smoothstep(0.0, 0.7, progress));
    float span = max(bottom - top, 0.5);
    if (q.y < top || q.y > top + span) return half4(0);
    float v = (q.y - top) / span;
    float f = smoothstep(w.y, t.y, q.y) * bend;
    float rowWidth = mix(width, size, f);
    float rowCentre = mix(cx, t.x, f);
    float u = (q.x - (rowCentre - rowWidth * 0.5)) / rowWidth;
    if (u < 0.0 || u > 1.0) return half4(0);
    half4 c = content.eval(fromWay(float2(w.x + u * width, w.y + v * height)));
    return c * half(1.0 - smoothstep(0.82, 1.0, progress));
}
"""

    /** Where [point] is from [window], for [SHADER]'s way: the side it's furthest out on, for the window's size. */
    fun way(window: Rect, point: Offset): Float {
        val dx = (point.x - window.center.x) / (window.width / 2f).coerceAtLeast(1f)
        val dy = (point.y - window.center.y) / (window.height / 2f).coerceAtLeast(1f)
        return if (abs(dy) >= abs(dx)) (if (dy > 0f) 0f else 1f) else (if (dx > 0f) 2f else 3f)
    }
}

/** The shader for a warp, made once; null where it can't be (before Android 13, or where it fails to build). */
@Composable
fun rememberWarper(): Warper? = remember {
    if (Build.VERSION.SDK_INT >= 33 && Build.FINGERPRINT != "robolectric") runCatching { Warper() }.getOrNull() else null
}

@RequiresApi(33)
class Warper {
    private val shader = RuntimeShader(Warp.SHADER)

    fun effect(window: Rect, point: Offset, size: Float, progress: Float): RenderEffect {
        shader.setFloatUniform("win", window.left, window.top, window.right, window.bottom)
        shader.setFloatUniform("target", point.x, point.y)
        shader.setFloatUniform("size", size)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("way", Warp.way(window, point))
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/**
 * Warps what's drawn in this (a layer the size of the screen, with the window at [window] in it) into [point],
 * [pointSize] across, as [progress] goes from 0 to 1: with [warper]'s shader where there is one, otherwise by
 * shrinking it into the point. Hidden while it's under way and the window's place isn't known yet.
 */
fun Modifier.warp(warper: Warper?, progress: () -> Float, window: () -> Rect, point: () -> Offset?, pointSize: Float, pad: Float): Modifier =
    graphicsLayer {
        val p = progress()
        val at = point()
        val w = window()
        renderEffect = null
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        if (p <= 0.001f) return@graphicsLayer
        if (at == null || w.isEmpty) {
            alpha = 0f
            return@graphicsLayer
        }
        if (warper != null && Build.VERSION.SDK_INT >= 33) {
            renderEffect = warper.effect(w.inflate(pad), at, pointSize, p)
        } else {
            val e = FastOutSlowInEasing.transform(p)
            transformOrigin = TransformOrigin(at.x / size.width.coerceAtLeast(1f), at.y / size.height.coerceAtLeast(1f))
            scaleX = 1f - e
            scaleY = 1f - e
            alpha = 1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
        }
    }
