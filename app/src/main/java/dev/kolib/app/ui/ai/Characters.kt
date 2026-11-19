package dev.kolib.app.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.AiSettings
import dev.kolib.app.ai.Prompts
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One of the AI's characters: one of the app's own (see [Mascot]), or one the user made from a character sheet. */
interface Character {
    val id: String

    /** Its name, until the user gives it another. */
    val defaultName: String

    /** What it is, in a word: a robot, a cat. */
    val kind: String

    /** How it is and how it talks: what its own instructions to the chat say about it. */
    val personality: String

    val art: MascotArt

    /** One the user made, which can be deleted. */
    val own: Boolean get() = false
}

/** What the character is called, here: the user's name for it, or its own. */
fun Character.nameIn(s: AiSettings): String = s.nameOf(id, defaultName)

/** Its own instructions for the chat: who it is, then what every reading companion keeps to. */
fun Character.ownInstructions(s: AiSettings): String = Prompts.asCharacter(nameIn(s), personality)

/** The instructions the chat has now: those chosen for the character it is, or the character's own. */
fun AiSettings.instructionsNow(): String = chatPrompt.ifEmpty { Characters.of(mascot).ownInstructions(this) }

/** The character the AI is, as chosen in the settings. */
@Composable
fun chosenCharacter(): Character {
    val app = LocalContext.current.applicationContext as KoLibApp
    Characters.load(app)
    val settings by app.ai.settings.collectAsState()
    val own by Characters.own.collectAsState()
    return own.firstOrNull { it.id == settings.mascot } ?: Mascot.of(settings.mascot)
}

/** All the characters there are to choose from: the app's, then the user's. */
@Composable
fun allCharacters(): List<Character> {
    Characters.load(LocalContext.current.applicationContext)
    val own by Characters.own.collectAsState()
    return Mascot.entries + own
}

/** The pictures on a character sheet, in its order: one of the character in each square. */
enum class SheetFrame { IDLE, BLINK, HELLO, READING, THINKING, WRITING, TALKING, HAPPY, SAD, ASLEEP, HELD, SCARED, FLYING, DIZZY, BUMPED, FACE }

/** A character the user made: [frames] cut from the sheet they filled in. */
class OwnCharacter(
    override val id: String,
    override val defaultName: String,
    override val personality: String,
    val frames: Map<SheetFrame, ImageBitmap>,
    val made: Long,
) : Character {
    override val kind: String get() = tr("Your own")
    override val own: Boolean get() = true
    override val art: MascotArt = OwnArt(frames)
}

/** A character drawn from its pictures: the one for what it's doing, moved about as any character is. */
internal class OwnArt(private val frames: Map<SheetFrame, ImageBitmap>) : MascotArt {
    override val head = Offset(0.5f, 0.3f)
    override val headR = 0.2f

    private fun pick(vararg wanted: SheetFrame): ImageBitmap? = wanted.firstNotNullOfOrNull { frames[it] } ?: frames[SheetFrame.IDLE] ?: frames.values.firstOrNull()

    /** The picture for what it's doing now. */
    internal fun frameFor(m: MascotMotion): ImageBitmap? = when (m.act) {
        Act.HELLO -> pick(SheetFrame.HELLO)
        Act.READING -> pick(SheetFrame.READING)
        Act.THINKING -> pick(SheetFrame.THINKING)
        Act.WRITING -> if (m.talk > 0.45f) pick(SheetFrame.TALKING, SheetFrame.WRITING) else pick(SheetFrame.WRITING)
        Act.HAPPY -> pick(SheetFrame.HAPPY)
        Act.SAD -> pick(SheetFrame.SAD)
        Act.SLEEPING -> pick(SheetFrame.ASLEEP, SheetFrame.BLINK)
        Act.HELD -> pick(SheetFrame.HELD)
        Act.SCARED -> pick(SheetFrame.SCARED, SheetFrame.HELD)
        Act.FLYING -> pick(SheetFrame.FLYING, SheetFrame.HELD)
        Act.DIZZY -> pick(SheetFrame.DIZZY)
        Act.BUMPED -> pick(SheetFrame.BUMPED)
        Act.IDLE -> when {
            m.wave > 0.5f || m.special > 0.1f -> pick(SheetFrame.HELLO)
            m.yawn > 0.3f -> pick(SheetFrame.ASLEEP, SheetFrame.BLINK)
            m.hum > 0.4f || m.wink > 0.4f -> pick(SheetFrame.HAPPY)
            m.open < 0.3f -> pick(SheetFrame.BLINK)
            else -> pick(SheetFrame.IDLE)
        }
    }

    private fun DrawScope.whole(image: ImageBitmap, s: Float) {
        val n = s.roundToInt()
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(n, n), filterQuality = FilterQuality.Medium)
    }

    override fun DrawScope.figure(m: MascotMotion, s: Float) {
        frameFor(m)?.let { whole(it, s) }
    }

    override fun DrawScope.icon(m: MascotMotion, s: Float) {
        (frames[SheetFrame.FACE] ?: frames[SheetFrame.IDLE])?.let { whole(it, s) }
    }
}

/** The user's characters, kept in the app's files: each a folder with its pictures and what it's called. */
object Characters {
    private val state = MutableStateFlow<List<OwnCharacter>>(emptyList())
    val own: StateFlow<List<OwnCharacter>> = state
    private var dir: File? = null

    /** Reads the user's characters, the first time. */
    @Synchronized
    fun load(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "characters")
        dir = d
        state.value = d.listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }?.sortedBy { it.made }.orEmpty()
    }

    /** The character [id]: one of the user's, or the app's (Bolt, if there's no such character). */
    fun of(id: String?): Character = state.value.firstOrNull { it.id == id } ?: Mascot.of(id)

    private fun read(folder: File): OwnCharacter? = runCatching {
        val about = JSONObject(File(folder, "character.json").readText())
        val frames = SheetFrame.entries.mapNotNull { f ->
            val file = File(folder, f.name.lowercase() + ".png")
            if (!file.isFile) null else BitmapFactory.decodeFile(file.path)?.let { f to it.asImageBitmap() }
        }.toMap()
        if (SheetFrame.IDLE !in frames) null
        else OwnCharacter(folder.name, about.optString("name", "?"), about.optString("personality", ""), frames, about.optLong("made", 0L))
    }.onFailure { AppLog.w("AI", "Couldn't read the character in ${folder.name}", it) }.getOrNull()

    /** Makes a character called [name] from [frames] cut from a sheet. */
    @Synchronized
    fun make(context: Context, name: String, personality: String, frames: Map<SheetFrame, Bitmap>): OwnCharacter {
        load(context)
        val folder = File(dir!!, "own-" + UUID.randomUUID().toString().take(8)).apply { mkdirs() }
        for ((f, b) in frames) File(folder, f.name.lowercase() + ".png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "character.json").writeText(
            JSONObject().put("name", name.trim()).put("personality", personality.trim()).put("made", System.currentTimeMillis()).toString()
        )
        val made = read(folder) ?: error("The character couldn't be read back")
        state.value = state.value + made
        return made
    }

    /** Deletes the user's character [id]. */
    @Synchronized
    fun delete(id: String) {
        val folder = dir?.let { File(it, id) } ?: return
        if (!id.startsWith("own-")) return
        folder.deleteRecursively()
        state.value = state.value.filterNot { it.id == id }
    }

    /** Forgets what's been read (for tests). */
    @Synchronized
    internal fun reset() {
        dir = null
        state.value = emptyList()
    }
}

/**
 * The character sheet: a grid of green squares, one for each picture of the character, with what goes in it written
 * under it. Filled in (by hand, or by an AI that makes pictures), it's cut back into the pictures here.
 */
object CharacterSheet {
    const val ASSET = "characters/sheet.png"
    const val WIDTH = 1280
    const val HEIGHT = 1440
    private const val COLUMNS = 4
    private const val CELL_W = 320
    private const val CELL_H = 360
    private const val SQUARE = 304
    private const val INSET = 8

    /** Each picture, cut from the sheet, is this many pixels square. */
    const val FRAME = 256

    /** What to tell an AI that makes pictures, along with the sheet, for it to fill it in. */
    const val INSTRUCTIONS = "This is a character sheet for KoLib, an e-book app. Fill it in with one character.\n" +
        "- Draw the same character in all 16 green squares, in the pose written under each square. Keep the character the " +
        "same size and in the same place in every square: whole body, standing, facing the viewer, feet near the bottom. " +
        "Square 16 is the face alone, close up.\n" +
        "- Keep the background of every square plain, flat green (#00FF00): no shadows, scenery or writing in the squares, " +
        "and nothing green on the character.\n" +
        "- Keep the sheet as it is: 1280 by 1440 pixels, with the squares and the words under them where they are.\n" +
        "- A bold, simple cartoon style with clear outlines works best, as it's shown small.\n" +
        "The character: (describe your character here)"

    /** The frames cut from a filled-in [sheet], with their backgrounds taken out; null if there's no character on it. */
    fun cut(sheet: Bitmap): Map<SheetFrame, Bitmap>? {
        val scaled = if (sheet.width == WIDTH && sheet.height == HEIGHT) sheet else Bitmap.createScaledBitmap(sheet, WIDTH, HEIGHT, true)
        val frames = LinkedHashMap<SheetFrame, Bitmap>()
        for ((i, f) in SheetFrame.entries.withIndex()) {
            val x = (i % COLUMNS) * CELL_W + INSET + 4
            val y = (i / COLUMNS) * CELL_H + INSET + 4
            val size = SQUARE - 8
            val square = Bitmap.createBitmap(scaled, x, y, size, size).copy(Bitmap.Config.ARGB_8888, true)
            clearBackground(square)
            if (!hasInk(square)) continue
            frames[f] = Bitmap.createScaledBitmap(square, FRAME, FRAME, true)
        }
        return frames.takeIf { SheetFrame.IDLE in it }
    }

    /** Reads a sheet from [bytes], no bigger than needed. */
    fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun hasInk(b: Bitmap): Boolean {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.count { (it ushr 24) > 128 } > px.size / 200
    }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun dist(c: Int, r0: Int, g0: Int, b0: Int): Float {
        val dr = r(c) - r0
        val dg = g(c) - g0
        val db = b(c) - b0
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    /**
     * Makes the square's background see-through: the colour round its edge, and all of it that's joined to the edge
     * (so the same colour inside the character, the whites of its eyes on a white sheet, stays). A sheet that's see-
     * through already is left as it is. Edges are softened, and a green background's tint taken off them.
     */
    internal fun clearBackground(bm: Bitmap) {
        val w = bm.width
        val h = bm.height
        val px = IntArray(w * h)
        bm.getPixels(px, 0, w, 0, 0, w, h)
        val edge = IntArray(2 * (w + h))
        var n = 0
        for (x in 0 until w) {
            edge[n++] = px[x]
            edge[n++] = px[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            edge[n++] = px[y * w]
            edge[n++] = px[y * w + w - 1]
        }
        if (edge.count { (it ushr 24) < 128 } > edge.size / 2) return
        val r0 = edge.map { r(it) }.sorted()[edge.size / 2]
        val g0 = edge.map { g(it) }.sorted()[edge.size / 2]
        val b0 = edge.map { b(it) }.sorted()[edge.size / 2]
        val near = 70f
        val soft = 120f
        val gone = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (!gone[i] && dist(px[i], r0, g0, b0) < near) {
                gone[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) {
            push(x)
            push((h - 1) * w + x)
        }
        for (y in 0 until h) {
            push(y * w)
            push(y * w + w - 1)
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        val greenBack = g0 > max(r0, b0) + 60
        for (i in px.indices) {
            if (gone[i]) {
                px[i] = 0
                continue
            }
            val c = px[i]
            val x = i % w
            val y = i / w
            val byGone = (x > 0 && gone[i - 1]) || (x < w - 1 && gone[i + 1]) || (y > 0 && gone[i - w]) || (y < h - 1 && gone[i + w])
            var a = c ushr 24
            if (byGone) {
                val d = dist(c, r0, g0, b0)
                if (d < soft) a = min(a, (((d - near) / (soft - near)).coerceIn(0f, 1f) * 255).roundToInt())
            }
            var gg = g(c)
            // A green background's tint taken off the character's edges (only there: a character may have green eyes).
            if (greenBack && byGone && gg > max(r(c), b(c))) gg = max(r(c), b(c)) + (gg - max(r(c), b(c))) / 4
            px[i] = (a shl 24) or (r(c) shl 16) or (gg shl 8) or b(c)
        }
        bm.setPixels(px, 0, w, 0, 0, w, h)
    }
}
