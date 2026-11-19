package dev.kolib.app.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ai.AiSettings
import dev.kolib.app.ai.AiSettingsStore
import dev.kolib.app.ai.PromptPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CharactersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        File(context.filesDir, "characters").deleteRecursively()
        Characters.reset()
    }

    @After
    fun done() = Characters.reset()

    /** The sheet as it comes, with a figure drawn in the squares [filled]: a red body with a white eye. */
    private fun sheet(filled: List<Int>, background: Int? = null): Bitmap {
        val b = context.assets.open(CharacterSheet.ASSET).use { BitmapFactory.decodeStream(it) }.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(b)
        val p = Paint().apply { isAntiAlias = true }
        for (i in 0 until 16) {
            val x = (i % 4) * 320f + 8
            val y = (i / 4) * 360f + 8
            if (background != null) {
                p.color = background
                c.drawRect(x, y, x + 304, y + 304, p)
            }
            if (i !in filled) continue
            p.color = Color.rgb(200, 30, 40)
            c.drawCircle(x + 152, y + 160, 90f, p)
            p.color = Color.WHITE
            c.drawCircle(x + 152, y + 140, 20f, p)
        }
        return b
    }

    @Test
    fun aFilledInSheetIsCutIntoItsPictures() {
        val frames = CharacterSheet.cut(sheet((0 until 16).toList()))
        assertNotNull(frames)
        assertEquals(SheetFrame.entries.toSet(), frames!!.keys)
        val idle = frames.getValue(SheetFrame.IDLE)
        assertEquals(CharacterSheet.FRAME, idle.width)
        // The green round it is gone; the figure, and the white of its eye, are there.
        assertEquals(0, Color.alpha(idle.getPixel(4, 4)))
        assertEquals(0, Color.alpha(idle.getPixel(idle.width - 5, idle.height - 5)))
        val body = idle.getPixel(idle.width / 2, (idle.height * 0.75f).toInt())
        assertEquals(255, Color.alpha(body))
        assertTrue(Color.red(body) > 150 && Color.green(body) < 80)
        val eye = idle.getPixel(idle.width / 2, (idle.height * 0.45f).toInt())
        assertEquals(Color.WHITE, eye)
    }

    @Test
    fun onAWhiteSheetTheWhiteInsideTheCharacterStays() {
        // Its eye is white, as the background is: only what's joined to the edge goes.
        val frames = CharacterSheet.cut(sheet(listOf(0), background = Color.WHITE))!!
        val idle = frames.getValue(SheetFrame.IDLE)
        assertEquals(0, Color.alpha(idle.getPixel(4, 4)))
        assertEquals(Color.WHITE, idle.getPixel(idle.width / 2, (idle.height * 0.45f).toInt()))
        // Only the squares drawn in.
        assertEquals(setOf(SheetFrame.IDLE), frames.keys)
    }

    @Test
    fun anEmptySheetHasNoCharacter() {
        assertNull(CharacterSheet.cut(sheet(emptyList())))
        // A character must be in the first square at least.
        assertNull(CharacterSheet.cut(sheet(listOf(5, 6))))
    }

    @Test
    fun aCharacterMadeIsKeptAndCanBeDeleted() {
        val frames = CharacterSheet.cut(sheet(listOf(0, 3, 15)))!!
        Characters.load(context)
        val made = Characters.make(context, " Captain ", "A grumpy pirate who loves poetry", frames)
        assertEquals("Captain", made.defaultName)
        assertTrue(made.own)
        assertSame(made, Characters.of(made.id))
        // Read again, as when KoLib next starts.
        Characters.reset()
        Characters.load(context)
        val again = Characters.of(made.id) as OwnCharacter
        assertEquals("Captain", again.defaultName)
        assertEquals(setOf(SheetFrame.IDLE, SheetFrame.READING, SheetFrame.FACE), again.frames.keys)
        // What it shows: its picture for what it's doing, or its Idle one when it has none.
        val art = again.art as OwnArt
        val reading = MascotMotion().apply { forced = Act.READING; step(1 / 60f) }
        assertSame(again.frames[SheetFrame.READING], art.frameFor(reading))
        val dizzy = MascotMotion().apply { forced = Act.DIZZY; step(1 / 60f) }
        assertSame(again.frames[SheetFrame.IDLE], art.frameFor(dizzy))
        Characters.delete(made.id)
        assertEquals(Mascot.ROBOT, Characters.of(made.id))
        assertFalse(File(context.filesDir, "characters/${made.id}").exists())
    }

    @Test
    fun eachCharacterTalksItsOwnWayAndKeepsItsInstructions() {
        // Its own instructions: its name and way of talking, then what every companion keeps to.
        val s = AiSettings(mascot = "owl")
        val owl = s.instructionsNow()
        assertTrue(owl.startsWith("You are Sage, a reading companion in an e-book app. You're a wise old owl"))
        assertTrue(owl.contains("Never reveal anything that happens later in a book"))
        // Named otherwise, it's called that.
        assertTrue(s.naming("owl", " Professor Hoot ").instructionsNow().startsWith("You are Professor Hoot,"))
        assertEquals("Sage", s.naming("owl", "Professor").naming("owl", " ").nameOf("owl", "Sage"))
        // Switched to another, each keeps the instructions it had, and has them again when switched back.
        val brief = PromptPreset("Brief", "Answer in a sentence.")
        val a = s.copy(presets = listOf(brief)).using(brief).switchedTo("cat")
        assertEquals("", a.chatPrompt)
        assertTrue(a.instructionsNow().startsWith("You are Mochi,"))
        assertEquals("Answer in a sentence.", a.instructionsOf("owl"))
        val b = a.using(brief).switchedTo("ghost").switchedTo("owl")
        assertEquals("Answer in a sentence.", b.chatPrompt)
        // Who's using the preset: the owl and the cat; the ghost has its own.
        assertEquals(listOf("owl", "cat"), b.usersOf(brief, listOf("robot", "owl", "cat", "ghost")))
        // Kept, and read back as they were.
        val store = AiSettingsStore(context)
        store.set(b.naming("cat", "Whiskers"))
        val read = AiSettingsStore(context).settings.value
        assertEquals("Whiskers", read.nameOf("cat", "Mochi"))
        assertEquals("Answer in a sentence.", read.instructionsOf("cat"))
        assertEquals(b.mascotPrompts, read.mascotPrompts)
    }
}
