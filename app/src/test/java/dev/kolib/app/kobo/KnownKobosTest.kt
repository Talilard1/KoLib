package dev.kolib.app.kobo

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ui.components.FileManagers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/** Someone with more than one Kobo: each keeps its own saved copy, and either can be the one shown. */
@RunWith(RobolectricTestRunner::class)
class KnownKobosTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun kobo(serial: String, model: String, at: Long, collections: Int = 0) = LastSeenStore.LastSeen(
        KoboDeviceInfo(model, null, KoboVersionInfo(serial, "4.38.23171", "00000000-0000-0000-0000-000000000386"), "UUID-$serial", null, 8_000_000_000L, 4_000_000_000L, false),
        KoboSnapshot(170, emptyList(), (1..collections).map { KoboCollection("Shelf $it", null, "id$it", null, true, false, null, null, emptyList()) }, emptyMap(), emptyList()),
        at, 1000L, false,
    )

    private val clara = kobo("N506B1111111", "Clara 2E", 1_000L, collections = 2)
    private val libra = kobo("N418C2222222", "Libra 2", 2_000L)
    private val twin = kobo("N506B3333333", "Clara 2E", 3_000L)

    @Test
    fun eachKoboKeepsItsOwnCopy() {
        val dir = File(context.filesDir, "seen-" + UUID.randomUUID())
        val store = LastSeenStore(dir)
        assertEquals(emptyList<KnownKobo>(), store.known())
        assertNull(store.selected)
        assertNull(store.load())

        store.save(clara)
        store.save(libra)
        val claraKey = LastSeenStore.keyOf(clara.info)
        val libraKey = LastSeenStore.keyOf(libra.info)
        assertNotEquals(claraKey, libraKey)
        // The one seen last comes first, and is the one shown.
        assertEquals(listOf(libraKey, claraKey), store.known().map { it.key })
        assertEquals(libraKey, store.selected)
        assertEquals(libra, store.load())
        assertEquals(clara, store.load(claraKey))
        val known = store.known().first { it.key == claraKey }
        assertEquals("Kobo Clara 2E", known.name)
        assertEquals("1111", known.serialEnd)
        assertEquals(2, known.collections)
        assertEquals(1_000L, known.readAtMillis)
        // Each has its own covers.
        assertNotEquals(store.coverDir(claraKey), store.coverDir(libraKey))
        assertEquals(store.coverDir(libraKey), store.coverDir)

        // Choosing the other shows it, and stays chosen for another look at the store.
        store.selected = claraKey
        assertEquals(clara, store.load())
        assertEquals(claraKey, LastSeenStore(dir).selected)
        assertEquals(clara, LastSeenStore(dir).load())

        // The same Kobo again replaces its own copy, not the other's.
        val later = clara.copy(readAtMillis = 5_000L)
        store.save(later)
        assertEquals(2, store.known().size)
        assertEquals(later, store.load(claraKey))
        assertEquals(libra, store.load(libraKey))

        // Forgetting one leaves the other, which is then the one shown.
        store.forget(claraKey)
        assertEquals(listOf(libraKey), store.known().map { it.key })
        assertEquals(libraKey, store.selected)
        assertNull(store.load(claraKey))
        assertFalse(store.coverDir(claraKey).exists())
    }

    @Test
    fun twoOfTheSameModelAreTwoKobos() {
        val store = LastSeenStore(File(context.filesDir, "seen-" + UUID.randomUUID()))
        store.save(clara)
        store.save(twin)
        assertEquals(listOf("3333", "1111"), store.known().map { it.serialEnd })
        assertEquals(listOf("Kobo Clara 2E", "Kobo Clara 2E"), store.known().map { it.name })
    }

    @Test
    fun theCopyFromBeforeEachKoboHadItsOwnIsThatKobosNow() {
        // As it was kept until now: one file, and one folder of covers, for whichever Kobo was last connected.
        val made = File(context.filesDir, "seen-" + UUID.randomUUID())
        LastSeenStore(made).save(clara)
        val key = LastSeenStore.keyOf(clara.info)
        val dir = File(context.filesDir, "seen-" + UUID.randomUUID()).apply { mkdirs() }
        File(made, "devices/$key/last-seen.json").copyTo(File(dir, "last-seen.json"))
        File(dir, "covers").mkdirs()
        File(dir, "covers/a.jpg").writeText("a cover")

        val store = LastSeenStore(dir)
        assertEquals(listOf(key), store.known().map { it.key })
        assertEquals(clara, store.load())
        assertEquals("a cover", File(store.coverDir(key), "a.jpg").readText())
        assertFalse(File(dir, "last-seen.json").exists())
        assertFalse(File(dir, "covers").exists())
        // An old copy that can't be read is dropped, not tripped over each time.
        val broken = File(context.filesDir, "seen-" + UUID.randomUUID()).apply { mkdirs() }
        File(broken, "last-seen.json").writeText("{ not json")
        assertNull(LastSeenStore(broken).load())
        assertFalse(File(broken, "last-seen.json").exists())
    }

    @Test
    fun filesPickedWithAnyFileManagerAreAllTaken() {
        val a = Uri.parse("content://files/a.epub")
        val b = Uri.parse("content://files/b.epub")
        // Several, as file managers hand them over; one, as some do; none.
        val many = Intent().apply { clipData = ClipData.newRawUri("", a).apply { addItem(ClipData.Item(b)) } }
        assertEquals(listOf(a, b), FileManagers.picked(Activity.RESULT_OK, many))
        assertEquals(listOf(a), FileManagers.picked(Activity.RESULT_OK, Intent().setData(a)))
        assertEquals(emptyList<Uri>(), FileManagers.picked(Activity.RESULT_CANCELED, many))
        assertEquals(emptyList<Uri>(), FileManagers.picked(Activity.RESULT_OK, null))
        // The first time, Android asks which app; nothing is remembered until one is chosen.
        assertNull(FileManagers.chosen(context))
        assertTrue(FileManagers.intent(context, arrayOf("application/epub+zip")).action == Intent.ACTION_CHOOSER)
    }
}
