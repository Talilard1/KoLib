package dev.kolib.app.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.FrameLayout
import dev.kolib.app.log.AppLog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a page turn needs from the screen: copies of it, a surface over it to draw on, and frames. */
interface TurnHost {
    val width: Int
    val height: Int
    val density: Float
    val touchSlop: Int
    val surfaceReady: Boolean
    /** Copies what's on screen where the page is: the page and anything drawn on it, never the surface. */
    fun copyScreen(done: (Bitmap?) -> Unit)
    fun draw(block: (Canvas) -> Unit): Boolean
    fun clear()
    /** Once the page underneath has drawn what it's been told to show. */
    fun whenDrawn(then: () -> Unit)
    fun afterFrames(n: Int, then: () -> Unit)
    fun postFrame(callback: (Long) -> Unit)
    fun cancelFrames()
    fun postDelayed(ms: Long, then: () -> Unit)
    fun nanoTime(): Long
    /** A copy that's done with, for reuse. */
    fun giveBack(b: Bitmap) {}
}

/** What a page turn needs from the reader. */
interface TurnPages {
    val pageTurn: PageTurn
    val spread: Boolean
    val selecting: Boolean
    /** Loading, or something shown over the page: no animation. */
    val blocked: Boolean
    val page: Int
    val pages: Int
    val paper: Int
    fun canMove(dir: Int): Boolean
    /** Moves the page on (or back) at once; false at the first or last page. */
    fun turnPageNow(dir: Int, done: (Boolean) -> Unit)
    /** Opens the next chapter, or the previous one at its end. */
    fun openChapter(dir: Int)
    /** Asks the page for another turn; the page decides whether it's within the chapter. */
    fun askForTurn(dir: Int)
}

/**
 * The page in the reader, with a surface over it for the page turns the app draws itself (Curl, Fade).
 * The surface sits above the whole window, so a copy of the window (PixelCopy) never includes it:
 * the next page can be copied while the surface still shows this one.
 */
/** Going this fast (dp a second) as it gets to a chapter's end, the page was flung there (see [ReaderFrame.scrolled]). */
internal const val PULL_FAST_DP = 900f
/**
 * Got to a chapter's end (or its start) fast, flung there or swiped on to it, a pull there takes a little more pulling:
 * in the swipe that got there, and in the touch straight after it, if that comes no later than this (ms) after the page
 * stopped (or the swipe was lifted). Nothing's held back; a quick run of swipes just doesn't fill the ring by chance.
 */
internal const val PULL_SETTLE_MS = 600L
/** How much further the ring fills over, for a pull at an end the page got to fast (see [PULL_SETTLE_MS]). */
internal const val PULL_FAST_SPAN = 1.5f

class ReaderFrame(context: Context, val controller: ReaderController) : FrameLayout(context) {
    val webView: WebView = controller.createView(context)
    val surface = TurnSurface(context)
    private val host = ViewTurnHost(this)
    val turner = PageTurner(host, controller.turnPages)

    init {
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Scrolling: how fast the page was going when it got to the foot of the chapter (or its top), for the pull on.
        webView.setOnScrollChangeListener { _, _, y, _, oldY -> scrolled(y - oldY, android.os.SystemClock.uptimeMillis()) }
        controller.turner = turner
        // Every chapter's pages are counted under the page, at its size, for page numbers through the book.
        controller.createCounter(this)
    }

    private var surfaceOn = false

    /** The surface is only there while Curl or Fade is chosen; Slide and None never touch it. */
    fun useSurface(on: Boolean) {
        if (on == surfaceOn) return
        surfaceOn = on
        if (on) addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        else {
            turner.cancel()
            removeView(surface)
        }
    }

    /**
     * A swipe up or down a side sets the brightness (the side, and where up it, as the reader's set: see
     * [ReaderSettings.startsLevelSwipe]): told the new level (see [ReaderSettings.level]).
     */
    var onLevel: ((Float) -> Unit)? = null
    /** The brightness level to start such a swipe from. */
    var currentLevel: () -> Float = { 0.5f }

    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var edgeTracking = false
    private var edgeActive = false
    private var edgeDownX = 0f
    private var edgeStartY = 0f
    private var edgeStartLevel = 0f

    /**
     * Scrolling: a pull on past the end of the chapter (1) or back past its start (-1), as the finger goes, with how full
     * the ring showing it is (0 to 1). It fills once the finger's come [pullDead] past where the pull was taken, over
     * [pullSpan] more, so it isn't started by chance. The page doesn't move while it's pulled, either way, till the
     * finger's lifted.
     */
    var onChapterPull: ((Int, Float) -> Unit)? = null
    /** That pull let go of (or taken away): whether the ring was full, and the next chapter (or the one before) is due. */
    var onChapterLetGo: ((Int, Boolean) -> Unit)? = null
    /** The page can scroll on (1) or back (-1) from where it is. */
    var canScroll: (Int) -> Boolean = { webView.canScrollVertically(it) }
    /** There's a chapter to pull on into (1) or back into (-1). */
    var hasChapter: (Int) -> Boolean = { false }
    private var pullDir = 0
    private var pullFrom = 0f
    private var pullDownX = 0f
    private var pullDownY = 0f
    private var pullLastY = 0f
    private var pullTwo = false
    private var pullFull = false
    /** When the finger went down. */
    private var pullDownAt = 0L
    /** Flung to the foot (on) or the top (back) just before the finger went down: a pull that way takes a little more. */
    private var pullOnFast = false
    private var pullBackFast = false
    /** The pull under way takes a little more pulling (see [PULL_SETTLE_MS]). */
    private var pullHard = false
    /** When the page was last scrolled, and how fast (pixels a second, down the chapter). */
    private var scrolledAt = 0L
    private var scrollSpeed = 0f
    /** When it came to a stop at the chapter's foot (or its top) going fast, flung there: none, if it didn't. */
    private var flungToFootAt = Long.MIN_VALUE / 2
    private var flungToTopAt = Long.MIN_VALUE / 2

    /**
     * The page scrolled by [dy] pixels at [at] (uptime, in ms). If that brought it to the chapter's foot (or its top) going
     * fast, a pull on (or back) in the swipe that did it, or in the touch straight after (if it comes as soon as the next of
     * a quick run of swipes would), takes a little more pulling to fill the ring. Brought there at a reading pace, or left to
     * stand still a moment, a pull fills as ever.
     */
    internal fun scrolled(dy: Int, at: Long) {
        if (dy == 0) return
        val dt = (at - scrolledAt).coerceIn(8L, 100L)
        val speed = dy * 1000f / dt
        // Smoothed over the last few frames, as a fling eases off.
        scrollSpeed = if (at - scrolledAt > 100L) speed else scrollSpeed * 0.4f + speed * 0.6f
        scrolledAt = at
        val fast = PULL_FAST_DP * density
        if (dy > 0 && !canScroll(1) && scrollSpeed > fast) flungToFootAt = at
        if (dy < 0 && !canScroll(-1) && -scrollSpeed > fast) flungToTopAt = at
    }

    /** A swipe in from the left (-1) or the right (1) edge: the side panel on that side starts to come out. */
    var onSideSwipe: ((Int) -> Unit)? = null
    /** As that swipe goes on: how far in from its edge the finger is, in pixels (the panel follows it). */
    var onSidePull: ((Float) -> Unit)? = null
    /** That swipe let go: how fast the finger was going in from the edge, in pixels a second (less than 0, back out). */
    var onSideLetGo: ((Float) -> Unit)? = null
    private var sideVelocity: VelocityTracker? = null
    /** The edge the panel being pulled out is on (-1 left, 1 right). */
    private var pulledFrom = 0

    /**
     * Whether the side edges are the reader's just now: a swipe in from one opens the panel on that side,
     * and Android's back swipe is kept off them. (Android allows that the whole way down an edge only
     * while the navigation bar is hidden, as it is while reading.)
     */
    var sideSwipes = false
        set(v) {
            if (field == v) return
            field = v
            excludeEdges()
        }

    private val density = context.resources.displayMetrics.density
    /**
     * How far a chapter's pull goes before its ring starts to fill, and how much further it fills over (half as far again,
     * at an end the page got to fast: see [PULL_SETTLE_MS]).
     */
    // Full a little past an eighth of a phone's height: further than a flick, not so far it's hard to get to.
    private val pullDead = 16 * density
    private val pullSpan = 120 * density
    /** How near the edge a swipe in has to start, as for Android's own back swipe. */
    private val sideZone = 24 * density
    /** How much of each edge Android's back swipe is kept off: more than [sideZone], to cover however wide it's set. */
    private val exclusionWidth = (48 * density).toInt()

    private fun excludeEdges() {
        systemGestureExclusionRects = if (!sideSwipes || width == 0 || height == 0) emptyList()
        else listOf(Rect(0, 0, exclusionWidth, height), Rect(width - exclusionWidth, 0, width, height))
    }

    /** The edge a touch started at (-1 left, 1 right, 0 neither), while it may yet be a swipe in from it. */
    private var side = 0
    /** The touch has become a swipe in from a side edge, and gets the rest of it. */
    private var sideTaken = false
    private var sideDownX = 0f
    private var sideDownY = 0f

    /**
     * A pull on past the end of a scrolled chapter (or back past its start) is watched for first, whatever the page is
     * doing with the touch (it may be scrolling with it, and keep it to itself): once one starts, the page is told the
     * touch is over, and the pull has the rest of it.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (pullDir != 0) {
            chapterPull(ev)
            return true
        }
        if (chapterPull(ev)) {
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (sideSwipe(ev)) return true
        if (edgeSwipe(ev)) return true
        // A swipe in from a side edge may be starting: the page doesn't turn under it.
        if (side != 0 && ev.actionMasked != MotionEvent.ACTION_DOWN) return false
        return turner.intercept(ev)
    }

    // Here once this has taken a touch over, or when nothing in the page took it to begin with (then
    // every part of it comes here, and none to onInterceptTouchEvent).
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (sideTaken) {
            sideSwipe(ev)
            return true
        }
        if (edgeActive) {
            edgeSwipe(ev)
            return true
        }
        if (sideSwipe(ev) || edgeSwipe(ev)) return true
        val turning = turner.touch(ev)
        // Kept while it may yet be a swipe in from a side edge, or up or down one for the brightness.
        return turning || (ev.actionMasked == MotionEvent.ACTION_DOWN && (side != 0 || edgeTracking))
    }

    /**
     * True once a swipe in from a side edge has started: one that starts right at the edge and goes
     * inward, rather than up or down (on the left, that sets the brightness) or a tap (a page turn).
     */
    private fun sideSwipe(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sideTaken = false
                side = when {
                    !sideSwipes || onSideSwipe == null || controller.selecting -> 0
                    ev.x <= sideZone -> -1
                    ev.x >= width - sideZone -> 1
                    else -> 0
                }
                sideDownX = ev.x
                sideDownY = ev.y
                sideVelocity?.recycle()
                sideVelocity = if (side != 0) VelocityTracker.obtain().also { it.addMovement(ev) } else null
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!sideTaken) side = 0
            MotionEvent.ACTION_MOVE -> {
                sideVelocity?.addMovement(ev)
                if (sideTaken) {
                    onSidePull?.invoke(fromEdge(ev.x))
                    return true
                }
                if (side != 0) {
                    val inward = (ev.x - sideDownX) * -side
                    val across = abs(ev.y - sideDownY)
                    if (inward > slop && inward > across * 1.5f) {
                        sideTaken = true
                        pulledFrom = side
                        side = 0
                        onSideSwipe?.invoke(pulledFrom)
                        onSidePull?.invoke(fromEdge(ev.x))
                        return true
                    }
                    if (across > slop || inward < -slop) side = 0
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = sideTaken
                if (was) {
                    sideVelocity?.addMovement(ev)
                    val speed = sideVelocity?.let { v ->
                        v.computeCurrentVelocity(1000)
                        v.xVelocity * -pulledFrom
                    } ?: 0f
                    onSideLetGo?.invoke(if (ev.actionMasked == MotionEvent.ACTION_CANCEL) 0f else speed)
                }
                sideVelocity?.recycle()
                sideVelocity = null
                sideTaken = false
                side = 0
                return was
            }
        }
        return false
    }

    /**
     * True once a pull on past the end of a scrolled chapter (or back past its start) has started: the page is at that end,
     * and the finger goes on that way, up or down, past where it can scroll. It then gets the rest of the touch. Nothing
     * moves on until the ring's full and the finger's lifted, so reading on to the last lines doesn't open the next
     * chapter by chance.
     */
    private fun chapterPull(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullDir = 0
                pullTwo = false
                pullFull = false
                pullDownX = ev.x
                pullDownY = ev.y
                pullLastY = ev.y
                pullDownAt = ev.eventTime
                // Flung there only just now, a pull in this touch takes a little more (see [scrolled]); the one after, as ever.
                pullOnFast = !canScroll(1) && ev.eventTime - flungToFootAt <= PULL_SETTLE_MS
                pullBackFast = !canScroll(-1) && ev.eventTime - flungToTopAt <= PULL_SETTLE_MS
                // From here on, the times are of getting there fast in this touch.
                flungToFootAt = Long.MIN_VALUE / 2
                flungToTopAt = Long.MIN_VALUE / 2
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (pullDir == 0) pullTwo = true
            MotionEvent.ACTION_MOVE -> {
                if (pullDir != 0) {
                    val travel = (pullFrom - ev.y) * pullDir
                    val full = ((travel - pullDead) / (if (pullHard) pullSpan * PULL_FAST_SPAN else pullSpan)).coerceIn(0f, 1f)
                    pullFull = full >= 1f
                    onChapterPull?.invoke(pullDir, full)
                    return true
                }
                val moved = ev.y - pullLastY
                pullLastY = ev.y
                // (Not a swipe in from a side, nor one up or down a side for the brightness: those go first.)
                if (pullTwo || sideTaken || edgeTracking || edgeActive || onChapterPull == null || !controller.settings.scrolling ||
                    controller.selecting
                ) return false
                val down = abs(ev.y - pullDownY)
                if (down < slop || down < abs(ev.x - pullDownX)) return false
                // At the foot (or the top), whether it was there when the finger went down or the finger's just brought it
                // there, the finger going on pulls. If it got there fast (in this touch, a swipe that's run on to the end,
                // or flung there just before it), it takes a little more pulling (see [scrolled]).
                val dir = when {
                    moved < 0 && !canScroll(1) && hasChapter(1) -> 1
                    moved > 0 && !canScroll(-1) && hasChapter(-1) -> -1
                    else -> 0
                }
                if (dir == 0) return false
                pullHard = if (dir > 0) pullOnFast || flungToFootAt >= pullDownAt else pullBackFast || flungToTopAt >= pullDownAt
                pullDir = dir
                pullFrom = ev.y
                pullFull = false
                onChapterPull?.invoke(dir, 0f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Run on to the end fast in this touch: the quick run of swipes goes on from when it's lifted.
                if (flungToFootAt >= pullDownAt) flungToFootAt = max(flungToFootAt, ev.eventTime)
                if (flungToTopAt >= pullDownAt) flungToTopAt = max(flungToTopAt, ev.eventTime)
                val dir = pullDir
                pullDir = 0
                if (dir != 0) {
                    onChapterLetGo?.invoke(dir, ev.actionMasked == MotionEvent.ACTION_UP && pullFull)
                    return true
                }
            }
        }
        return false
    }

    /** How far in from the edge the panel being pulled out is on [x] is. */
    private fun fromEdge(x: Float): Float = (if (pulledFrom < 0) x else width - x).coerceAtLeast(0f)

    /** True once a swipe up or down a side that sets the brightness has started (it then gets the rest of the touch). */
    private fun edgeSwipe(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeTracking = onLevel != null && !controller.selecting &&
                    controller.settings.startsLevelSwipe(ev.x, ev.y, width.toFloat(), height.toFloat())
                edgeActive = false
                edgeDownX = ev.x
                edgeStartY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (edgeActive) {
                    // The whole range, shaded to brightest, over most of the screen's height.
                    val range = 1f + ReaderSettings.MAX_DIM
                    val v = edgeStartLevel - (ev.y - edgeStartY) / (height * 0.75f) * range
                    onLevel?.invoke(v.coerceIn(-ReaderSettings.MAX_DIM, 1f))
                    return true
                }
                if (edgeTracking) {
                    val dx = ev.x - edgeDownX
                    val dy = ev.y - edgeStartY
                    if (kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) {
                        edgeTracking = false
                        edgeActive = true
                        edgeStartY = ev.y
                        edgeStartLevel = currentLevel()
                        return true
                    }
                    if (kotlin.math.abs(dx) > slop) edgeTracking = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = edgeActive
                edgeActive = false
                edgeTracking = false
                return was
            }
        }
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        turner.cancel()
        excludeEdges()
    }

    fun release() {
        turner.release()
        host.pool.clear()
        if (controller.turner === turner) controller.turner = null
    }
}

/** A see-through surface above the page that a turn is drawn on. Touches pass through it. */
class TurnSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    var ready = false
        private set

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        isClickable = false
        isFocusable = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ready = true
        clear()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = clear()

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ready = false
    }

    /** Draws a frame; false if there's no surface to draw on. */
    fun draw(block: (Canvas) -> Unit): Boolean {
        if (!ready) return false
        val canvas = try {
            holder.lockHardwareCanvas()
        } catch (e: Exception) {
            null
        } ?: return false
        try {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            block(canvas)
        } catch (e: Exception) {
            AppLog.w("Reader", "A page turn couldn't be drawn", e)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
        return true
    }

    fun clear() {
        draw { }
    }
}

/** A few screen-sized pictures, kept for the next turn rather than made afresh each time. */
class BitmapPool {
    private val free = ArrayDeque<Bitmap>()

    fun take(w: Int, h: Int): Bitmap? {
        while (free.isNotEmpty()) {
            val b = free.removeFirst()
            if (b.width == w && b.height == h && !b.isRecycled) return b
            b.recycle()
        }
        return null
    }

    fun give(b: Bitmap) {
        if (b.isRecycled) return
        if (free.size < 3) free.addLast(b) else b.recycle()
    }

    fun clear() {
        free.forEach { it.recycle() }
        free.clear()
    }
}

/** The real screen: PixelCopy of the window, the SurfaceView, and the Choreographer. */
class ViewTurnHost(private val frame: ReaderFrame) : TurnHost {
    private val main = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    val pool = BitmapPool()
    override val width get() = frame.width
    override val height get() = frame.height
    override val density = frame.resources.displayMetrics.density
    override val touchSlop = ViewConfiguration.get(frame.context).scaledTouchSlop
    override val surfaceReady get() = frame.surface.ready && activity() != null

    private fun activity(): Activity? {
        var c: Context? = frame.context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    override fun copyScreen(done: (Bitmap?) -> Unit) {
        val window = activity()?.window
        val w = frame.width
        val h = frame.height
        if (window == null || w <= 0 || h <= 0) {
            done(null)
            return
        }
        val loc = IntArray(2)
        frame.getLocationInWindow(loc)
        val bmp = try {
            pool.take(w, h) ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            done(null)
            return
        }
        try {
            PixelCopy.request(window, Rect(loc[0], loc[1], loc[0] + w, loc[1] + h), bmp, { result ->
                if (result == PixelCopy.SUCCESS) done(bmp) else {
                    AppLog.w("Reader", "Couldn't copy the page for the turn ($result)")
                    pool.give(bmp)
                    done(null)
                }
            }, main)
        } catch (e: Exception) {
            AppLog.w("Reader", "Couldn't copy the page for the turn", e)
            pool.give(bmp)
            done(null)
        }
    }

    override fun draw(block: (Canvas) -> Unit) = frame.surface.draw(block)

    override fun clear() = frame.surface.clear()

    override fun whenDrawn(then: () -> Unit) {
        val view = frame.webView
        try {
            view.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.invalidate()
                    afterFrames(2, then)
                }
            })
        } catch (e: Exception) {
            afterFrames(3, then)
        }
    }

    override fun afterFrames(n: Int, then: () -> Unit) {
        if (n <= 0) then() else choreographer.postFrameCallback { afterFrames(n - 1, then) }
    }

    private var frameCallback: Choreographer.FrameCallback? = null

    override fun postFrame(callback: (Long) -> Unit) {
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        val cb = Choreographer.FrameCallback { now ->
            frameCallback = null
            callback(now)
        }
        frameCallback = cb
        choreographer.postFrameCallback(cb)
    }

    override fun cancelFrames() {
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
    }

    override fun postDelayed(ms: Long, then: () -> Unit) {
        main.postDelayed(then, ms)
    }

    override fun nanoTime() = System.nanoTime()

    /** Back for reuse once the surface has drawn its last frame with it. */
    override fun giveBack(b: Bitmap) = afterFrames(2) { pool.give(b) }
}

/**
 * Turns pages with Curl, Sweep or Fade. A turn: copy the screen; show the copy on the surface; move the
 * page underneath on (or open the next chapter); then animate the copy away, revealing the new page.
 * Turning back in single-page view, the previous page comes back over this one, so that page is
 * copied too once it's underneath. In two-page view the right-hand page turns over onto the left, and
 * its back is the new left-hand page.
 */
class PageTurner(private val host: TurnHost, private val pages: TurnPages) {

    private companion object {
        /** How soft the sweep's edge is, as a share of the way across. */
        const val SWEEP_EDGE = 0.16f
        /** A fade under a finger is a wash across the page: its soft edge is as wide as the page. */
        const val FADE_EDGE = 1f
        /** The most a sweep or a fade leans to follow the finger: 35 degrees from straight across. */
        const val MAX_LEAN = 0.61f
        /** A drag turns the page right over after this share of the screen's width, at most... */
        const val DRAG_MOST = 0.55f
        /** ...and at least (a drag starting near the far side). */
        const val DRAG_LEAST = 0.3f
    }

    private enum class Phase { IDLE, PREPARING, DRAGGING, ANIMATING, WAITING }

    private class Turn(val dir: Int, val kind: PageTurn, val edge: Boolean, val spread: Boolean, val anchorFrac: Float) {
        var a: Bitmap? = null
        var b: Bitmap? = null
        var curl: PageCurl? = null
        /** Single-page view, turning back: the previous page comes back over this one. */
        val comingBack get() = !spread && dir < 0 && kind == PageTurn.CURL
        /** How far through the turn, 0 to 1. */
        var p = 0f
        var bendY = 0f
        /** The page underneath has been asked to move on (so a cancelled turn has to move it back). */
        var moved = false
        /** The page underneath shows the new page, so it can be seen where the leaf has lifted. */
        var live = false
        var dragging = false
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var finished = false
        /**
         * The way a sweep or a fade travels over the page, as a direction of length one: straight across
         * for a tap; under a finger or a pen, the way that's being dragged, so its edge leans with the hand.
         */
        var wayX = -dir.toFloat()
        var wayY = 0f
        /** Dragged at some point (a fade then washes across the page the finger's way, rather than all at once). */
        var dragged = false
        /** A drag at a chapter's end let go short of turning: the chapter it was in is opening again. */
        var restoring = false
    }

    private var phase = Phase.IDLE
    private var turn: Turn? = null
    private var lastDir = 1
    /** Turns asked for while one was being drawn, still to do. */
    private var owed = 0
    private var hurry = false

    val busy: Boolean get() = phase != Phase.IDLE

    /** For tests: whether a copy is up over the page, and how far through its turn. */
    internal val showing: Boolean get() = turn != null
    internal val progress: Float get() = turn?.p ?: 0f

    private fun mode(): PageTurn? = when (pages.pageTurn) {
        PageTurn.CURL -> PageTurn.CURL
        PageTurn.SWEEP -> PageTurn.SWEEP
        PageTurn.FADE -> PageTurn.FADE
        else -> null
    }

    private fun canAnimate(): Boolean = mode() != null && host.surfaceReady && host.width > 0 && host.height > 0 && !pages.blocked

    // ---- what the page asks for ------------------------------------------------------------------

    /** A page turn within the chapter (a tap, a key, a swipe the page saw). False if it wasn't taken. */
    fun turn(dir: Int, yFrac: Float): Boolean {
        val m = mode() ?: return false
        if (busy) {
            owe(dir)
            return true
        }
        if (!canAnimate()) return false
        start(Turn(dir, m, edge = false, spread = pages.spread && m == PageTurn.CURL, anchorFrac = yFrac.coerceIn(0.05f, 0.95f)))
        return true
    }

    /** Past the first or last page: turn into the next or previous chapter. False if it wasn't taken. */
    fun edge(dir: Int): Boolean {
        val m = mode() ?: return false
        if (!pages.canMove(dir)) return false
        if (busy) {
            owe(dir)
            return true
        }
        if (!canAnimate()) return false
        start(Turn(dir, m, edge = true, spread = pages.spread && m == PageTurn.CURL, anchorFrac = 0.8f))
        return true
    }

    /** The chapter a turn was waiting for is laid out. */
    fun chapterReady() {
        val t = turn ?: return
        if (!t.edge || !t.moved) return
        if (t.restoring) {
            // Back in the chapter the drag started in: take the copy away once it's on screen.
            host.whenDrawn { if (turn === t) end(t) }
            return
        }
        host.whenDrawn {
            if (turn !== t) return@whenDrawn
            t.live = true
            // The page coming back starts from fully turned away (unless a finger is holding it).
            if (t.comingBack && !t.dragging) t.p = 0f
            if (needsB(t)) captureB(t) { if (phase == Phase.WAITING) runTurn(t) else render() }
            else if (phase == Phase.WAITING) runTurn(t)
            render()
        }
    }

    private fun owe(dir: Int) {
        if (dir != lastDir) owed = 0
        lastDir = dir
        owed = min(owed + 1, 3)
        hurry = true
        // Finish the turn under way quickly, so the next follows straight on.
        val a = anim
        val t = turn
        if (a != null && t != null && a.to == 1f && phase == Phase.ANIMATING && !t.dragging) {
            anim = Anim(t.p, 1f, t.bendY, host.nanoTime(), 110_000_000L, a.done)
        }
    }

    // ---- touch: a turn follows the finger -----------------------------------------------------------

    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var velocity: VelocityTracker? = null

    fun intercept(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A drag whose end went missing: let it go.
                turn?.let { t -> if (t.dragging) release(t, cancelled = true) }
                // Curl, Sweep and Fade all follow the finger, and go on or back when it lets go.
                tracking = mode() != null && !busy && !pages.selecting && canAnimate()
                downX = ev.x
                downY = ev.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                if (!tracking || pages.selecting) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > host.touchSlop && abs(dx) > abs(dy) * 1.2f) {
                    tracking = false
                    return beginDrag(if (dx < 0) 1 else -1, ev)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }

    fun touch(ev: MotionEvent): Boolean {
        val t = turn ?: return false
        if (!t.dragging) return false
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                t.lastX = ev.x
                t.p = dragProgress(t)
                // The held point follows the finger up and down too (it lands where the fold mirrors it).
                t.bendY = (t.curl?.anchorY ?: 0f) + (ev.y - t.downY) / 2
                lean(t, ev.y)
                render()
            }
            MotionEvent.ACTION_UP -> release(t, cancelled = false)
            MotionEvent.ACTION_CANCEL -> release(t, cancelled = true)
        }
        return true
    }

    private fun beginDrag(dir: Int, ev: MotionEvent): Boolean {
        val edge = if (dir > 0) pages.page >= pages.pages - 1 else pages.page <= 0
        if (edge && !pages.canMove(dir)) return false
        val kind = mode() ?: return false
        val t = Turn(dir, kind, edge = edge, spread = pages.spread && kind == PageTurn.CURL, anchorFrac = (downY / host.height).coerceIn(0.05f, 0.95f))
        t.dragging = true
        t.dragged = true
        t.downX = downX
        t.downY = downY
        t.lastX = ev.x
        lean(t, ev.y)
        start(t)
        return true
    }

    /**
     * A sweep or a fade travels the way the finger is going: dragged up or down as well as across, its edge
     * leans to stay square to the hand's path (as a curl's fold does), up to [MAX_LEAN].
     */
    private fun lean(t: Turn, y: Float) {
        val across = max(abs(t.lastX - t.downX), host.touchSlop.toFloat())
        val angle = atan2(y - t.downY, across).coerceIn(-MAX_LEAN, MAX_LEAN)
        t.wayX = -t.dir * cos(angle)
        t.wayY = sin(angle)
    }

    /**
     * How far a drag has turned the page. It's right over once the finger has gone a little over half
     * way across the screen (or to the far side, if it started nearer than that), so turning a page
     * on a big screen held sideways doesn't take a sweep of the whole screen.
     */
    private fun dragProgress(t: Turn): Float {
        val dx = max(0f, -t.dir * (t.lastX - t.downX))
        val room = if (t.dir > 0) t.downX else host.width - t.downX
        val need = room.coerceIn(host.width * DRAG_LEAST, host.width * DRAG_MOST)
        return (dx / need).coerceIn(0f, 1f)
    }

    private fun release(t: Turn, cancelled: Boolean) {
        t.dragging = false
        val v = velocity?.let { it.computeCurrentVelocity(1000); it.xVelocity } ?: 0f
        velocity?.recycle()
        velocity = null
        val fling = 600 * host.density
        val toward = -t.dir * v // positive: moving the way the page turns
        val commit = !cancelled && (toward > fling || (t.p > 0.3f && toward > -fling))
        when {
            // Let go before the copy of the page was ready (a quick flick): a swipe that was to turn the page
            // still does, played out from the start once the copy is up, as a tap's turn is. Less is dropped.
            t.a == null -> if (commit) t.p = 0f else end(t)
            // The next chapter isn't on screen yet: the leaf waits where it is, and goes on once it is.
            commit && t.edge && !t.live -> phase = Phase.WAITING
            commit -> animate(t, to = 1f) { finish(t) }
            else -> animate(t, to = 0f) { undo(t) }
        }
    }

    // ---- a turn, start to end ---------------------------------------------------------------------

    private fun start(t: Turn) {
        turn = t
        phase = Phase.PREPARING
        lastDir = t.dir
        // However things go, never leave a copy over the page for long.
        host.postDelayed(6000) {
            if (turn === t && phase != Phase.DRAGGING && phase != Phase.ANIMATING) {
                AppLog.w("Reader", "A page turn stalled; showing the page.")
                end(t)
            }
        }
        host.copyScreen { bmp ->
            if (turn !== t) {
                bmp?.let { host.giveBack(it) }
                return@copyScreen
            }
            if (bmp == null) {
                fallBack(t)
                return@copyScreen
            }
            t.a = bmp
            setUp(t)
            render()
            // With the copy up, what's underneath moves on: the next page, or the next chapter (opened
            // straight away, even for a drag, so it shows where the leaf lifts while it's held).
            if (t.dragging) phase = Phase.DRAGGING
            host.afterFrames(1) {
                if (turn !== t) return@afterFrames
                if (t.edge) openUnderneath(t) else moveUnderneath(t)
            }
        }
    }

    private fun openUnderneath(t: Turn) {
        if (!t.dragging) phase = Phase.WAITING
        t.moved = true
        pages.openChapter(t.dir)
    }

    /** With the copy up, the page underneath goes on to the next page. */
    private fun moveUnderneath(t: Turn) {
        t.moved = true
        pages.turnPageNow(t.dir) { moved ->
            if (turn !== t) return@turnPageNow
            if (!moved) {
                t.moved = false
                end(t)
                return@turnPageNow
            }
            host.whenDrawn {
                if (turn !== t) return@whenDrawn
                t.live = true
                render()
                if (needsB(t)) captureB(t) {
                    if (t.comingBack && !t.dragging && phase == Phase.PREPARING) runTurn(t) else render()
                }
                // A sweep uncovers the new page, so it starts once that's there to uncover.
                if (t.kind == PageTurn.SWEEP && !t.dragging && phase == Phase.PREPARING) runTurn(t)
            }
            if (t.dragging) phase = Phase.DRAGGING
            else if (!t.comingBack && t.kind != PageTurn.SWEEP) runTurn(t)
        }
    }

    /** The new page is needed as a picture: for the back of a turning leaf, or the page coming back. */
    private fun needsB(t: Turn) = t.kind == PageTurn.CURL && (t.spread || t.comingBack)

    private fun captureB(t: Turn, then: () -> Unit) {
        host.copyScreen { bmp ->
            if (turn !== t) {
                bmp?.let { host.giveBack(it) }
                return@copyScreen
            }
            t.b = bmp
            if (bmp == null && t.comingBack) {
                // Can't show the page coming back without a picture of it: just show it.
                end(t)
                return@copyScreen
            }
            then()
        }
    }

    private fun runTurn(t: Turn) {
        val ms = when (t.kind) {
            PageTurn.FADE -> 230L
            PageTurn.SWEEP -> 700L
            else -> 480L
        }
        animate(t, to = 1f, duration = if (hurry) ms / 2 else ms) { finish(t) }
    }

    private fun setUp(t: Turn) {
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        if (t.kind == PageTurn.CURL) {
            val curl = when {
                t.spread && t.dir > 0 -> PageCurl(w / 2, 1, w / 2, h, host.density)
                t.spread -> PageCurl(w / 2, -1, w / 2, h, host.density)
                else -> PageCurl(0f, 1, w, h, host.density)
            }
            curl.anchorY = t.anchorFrac * h
            t.curl = curl
            t.bendY = curl.anchorY
        }
        if (t.dragging) t.p = dragProgress(t)
    }

    private fun finish(t: Turn) {
        if (turn !== t) return
        t.finished = true
        end(t)
    }

    /** A drag let go short of turning: back to how things were. */
    private fun undo(t: Turn) {
        if (turn !== t) return
        if (!t.moved) {
            end(t)
            return
        }
        if (t.edge) {
            // The next chapter opened underneath: open the one the drag started in again, where it was
            // (its end, or its start), with the copy kept up until it's back on screen.
            t.restoring = true
            t.live = false
            phase = Phase.WAITING
            pages.openChapter(-t.dir)
            return
        }
        // Put the page underneath back, then take the copy away once it's showing.
        pages.turnPageNow(-t.dir) {
            host.whenDrawn { if (turn === t) end(t) }
        }
    }

    /** No copy of the screen: turn without the animation. */
    private fun fallBack(t: Turn) {
        if (turn !== t) return
        val dir = t.dir
        val edge = t.edge
        end(t)
        if (edge) pages.openChapter(dir) else pages.turnPageNow(dir) { }
    }

    private fun end(t: Turn) {
        anim = null
        host.cancelFrames()
        host.clear()
        t.a?.let { host.giveBack(it) }
        t.b?.let { host.giveBack(it) }
        t.a = null
        t.b = null
        if (turn === t) turn = null
        phase = Phase.IDLE
        hurry = false
        if (owed > 0 && t.finished) {
            // Turns asked for meanwhile: carry on, quickly, once the new page is on screen.
            owed--
            val dir = lastDir
            host.afterFrames(2) {
                if (busy) return@afterFrames
                hurry = true
                pages.askForTurn(dir)
            }
        } else if (!t.finished) {
            owed = 0
        }
    }

    fun cancel() {
        val t = turn ?: return
        owed = 0
        end(t)
    }

    fun release() {
        cancel()
        velocity?.recycle()
        velocity = null
    }

    // ---- animation and drawing ---------------------------------------------------------------------

    private var anim: Anim? = null

    private class Anim(val from: Float, val to: Float, val fromY: Float, val start: Long, val nanos: Long, val done: () -> Unit)

    private fun animate(t: Turn, to: Float, duration: Long? = null, done: () -> Unit) {
        val distance = abs(to - t.p)
        val ms = duration ?: (140 + 320 * distance).toLong()
        phase = Phase.ANIMATING
        anim = Anim(t.p, to, t.bendY, host.nanoTime(), ms * 1_000_000, done)
        host.postFrame(::tick)
    }

    private fun tick(now: Long) {
        val a = anim ?: return
        val t = turn ?: return
        val f = ((now - a.start).toFloat() / a.nanos).coerceIn(0f, 1f)
        val e = if (a.from == 0f && a.to == 1f) easeInOut(f) else easeOut(f)
        t.p = a.from + (a.to - a.from) * e
        val anchor = t.curl?.anchorY ?: 0f
        // A tapped page lifts by its corner, the near one first; a dragged one settles flat.
        // (Held low, the corner rises as it turns; held high, it drops.)
        val lean = if (a.from == 0f && a.to == 1f) host.height * 0.05f * sin(PI.toFloat() * t.p) * (if (t.anchorFrac > 0.5f) -1 else 1) else 0f
        t.bendY = a.fromY + (anchor - a.fromY) * e + lean
        render()
        if (f >= 1f) {
            anim = null
            a.done()
        } else host.postFrame(::tick)
    }

    private fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2
    private fun easeOut(x: Float) = 1 - (1 - x).let { it * it * it }

    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint()
    /** Takes away what it's drawn over (for the sweep), as much as it's opaque. */
    private val erase = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT) }

    /** Draws the current frame of the turn, as the surface shows it. */
    internal fun drawFrame(c: Canvas) {
        val t = turn ?: return
        val a = t.a ?: return
        val w = host.width.toFloat()
        val h = host.height.toFloat()
        val paper = pages.paper
        when (t.kind) {
            PageTurn.FADE -> if (t.dragged) {
                // Under a finger the old page washes away the way it's dragged, the near side first.
                c.drawBitmap(a, 0f, 0f, plain)
                if (t.live) wipe(c, t, w, h, FADE_EDGE)
            } else {
                plain.alpha = if (!t.live) 255 else (255 * (1 - t.p)).toInt().coerceIn(0, 255)
                c.drawBitmap(a, 0f, 0f, plain)
                plain.alpha = 255
            }
            PageTurn.SWEEP -> {
                c.drawBitmap(a, 0f, 0f, plain)
                // The old page is wiped away from the side the page turns from (the right going on, the left
                // going back) across to the other, with a soft edge, uncovering the new page underneath.
                if (t.live) wipe(c, t, w, h, SWEEP_EDGE)
            }
            else -> {
                val curl = t.curl ?: return
                fill.color = paper
                // What lies under the leaf.
                if (t.comingBack) {
                    c.drawBitmap(a, 0f, 0f, plain)
                } else if (t.spread) {
                    // The other page stays as it was until the leaf lands on it.
                    val other = if (t.dir > 0) Rect(0, 0, (w / 2).toInt(), h.toInt()) else Rect((w / 2).toInt(), 0, w.toInt(), h.toInt())
                    c.drawBitmap(a, other, RectF(other), plain)
                    if (!t.live) c.drawRect(curl.leafRect, fill)
                } else if (!t.live) {
                    c.drawRect(0f, 0f, w, h, fill)
                }
                if (t.comingBack) {
                    val b = t.b ?: return
                    curl.draw(c, b, null, paper, curl.pose(curl.width * t.p, t.bendY))
                } else {
                    curl.draw(c, a, t.b, paper, curl.pose(curl.width * (1 - t.p), t.bendY))
                }
            }
        }
    }

    /**
     * Takes the old page away up to an edge that has travelled [Turn.p] of the way over the page in the turn's
     * direction ([Turn.wayX], [Turn.wayY]): gone behind the edge, whole ahead of it, and fading between over
     * [soft] of the way. The edge is square to the direction, so it's upright for a turn straight across.
     */
    private fun wipe(c: Canvas, t: Turn, w: Float, h: Float, soft: Float) {
        // How far along the direction each corner of the page is: the edge starts before the nearest and ends past the farthest.
        val corners = floatArrayOf(0f, w * t.wayX, h * t.wayY, w * t.wayX + h * t.wayY)
        val near = corners.min()
        val range = (corners.max() - near).coerceAtLeast(1f)
        val front = near + range * t.p * (1 + soft)
        val gone = front - range * soft
        erase.shader = LinearGradient(t.wayX * gone, t.wayY * gone, t.wayX * front, t.wayY * front, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, erase)
        erase.shader = null
    }

    private fun render() {
        if (turn?.a == null) return
        host.draw { c -> drawFrame(c) }
    }
}
