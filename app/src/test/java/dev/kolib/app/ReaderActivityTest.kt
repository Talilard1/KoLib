package dev.kolib.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.comic.ComicOwn
import dev.kolib.app.comic.ComicOwnStore
import dev.kolib.app.comic.ComicRotation
import dev.kolib.app.comic.TestComics
import dev.kolib.app.epub.TestBooks
import dev.kolib.app.library.BookSource
import dev.kolib.app.reader.ReaderRotation
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Books and comics are read on the reader's own screen, as Mihon's are: it asks for the way round chosen for them as it
 * opens, before it's shown, and the library's screen is left as it was under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi")
class ReaderActivityTest {

    private val app get() = ApplicationProvider.getApplicationContext<KoLibApp>()

    @After
    fun backToAnyWay() {
        app.readerSettings.update { it.copy(rotation = ReaderRotation.AUTO) }
    }

    private fun add(name: String, file: File): String = runBlocking {
        if (!app.library.loaded) app.library.load()
        app.library.import(name, { file.inputStream() }, BookSource.FILE)
        app.library.books.value.first { it.originalName == name || it.title == name.substringBeforeLast('.') }.id
    }

    private fun idle() = repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)) }

    private fun open(id: String): ReaderActivity {
        val intent = Intent(app, ReaderActivity::class.java).putExtra("dev.kolib.app.READ_BOOK", id)
        val controller = Robolectric.buildActivity(ReaderActivity::class.java, intent).create()
        // Asked for as it's made, before it's shown.
        return controller.get().also {
            controller.start().resume().visible()
            idle()
        }
    }

    @Test
    fun aBookOpensTurnedTheWayChosenForIt() {
        val id = add("garden-${UUID.randomUUID()}.epub", TestBooks.garden(File(app.cacheDir, "garden-${UUID.randomUUID()}.epub")))
        app.readerSettings.update { it.copy(rotation = ReaderRotation.PORTRAIT) }
        val reader = open(id)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, reader.requestedOrientation)
        assertFalse(reader.isFinishing)
    }

    @Test
    fun aComicOpensTurnedTheWayChosenForItsSeries() {
        val file = TestComics.comic(File(app.cacheDir, "lantern-${UUID.randomUUID()}.cbz"), pages = 3, info = false)
        val id = add("Lantern ${UUID.randomUUID()}.cbz", file)
        ComicOwnStore.get(app).set("b:$id", ComicOwn(rotation = ComicRotation.LOCKED_LANDSCAPE))
        val reader = open(id)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, reader.requestedOrientation)
    }

    @Test
    fun aBookThatsGoneClosesTheReader() {
        val intent = Intent(app, ReaderActivity::class.java).putExtra("dev.kolib.app.READ_BOOK", "no-such-book")
        val reader = Robolectric.buildActivity(ReaderActivity::class.java, intent).setup().get()
        // The library's read from storage first, off the main thread, which can take a while when the tests are busy.
        val end = System.currentTimeMillis() + 15_000
        while (!reader.isFinishing && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue(reader.isFinishing)
    }

    @Test
    fun theLibraryStaysInUseWithAReaderOverIt() {
        val id = add("garden-${UUID.randomUUID()}.epub", TestBooks.garden(File(app.cacheDir, "garden-${UUID.randomUUID()}.epub")))
        val main = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN)).setup()
        idle()
        assertTrue(app.inUse.value)
        // The reader opens over the library's screen, which stops: KoLib is still in use.
        val intent = Intent(app, ReaderActivity::class.java).putExtra("dev.kolib.app.READ_BOOK", id)
        val reader = Robolectric.buildActivity(ReaderActivity::class.java, intent).create().start().resume().visible()
        main.pause().stop()
        idle()
        assertTrue(app.inUse.value)
        // Home from the reader: out of use.
        reader.pause().stop()
        idle()
        assertFalse(app.inUse.value)
        reader.destroy()
        main.destroy()
    }
}
