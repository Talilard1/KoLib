package dev.kolib.app.update

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ai.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Keeping KoLib up to date from its releases on GitHub. */
@RunWith(RobolectricTestRunner::class)
class UpdatesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun done() = scope.cancel()

    private fun release(tag: String = "v2.1", body: String = "### What's new\n\n- **Faster** look-up\n- See [the page](https://example.org)\n", files: List<String> = listOf("arm64-v8a", "armeabi-v7a", "universal", "x86", "x86_64")) = """{
      "tag_name": "$tag", "name": "KoLib ${tag.removePrefix("v")}", "html_url": "https://github.com/Talilard1/KoLib/releases/tag/$tag",
      "body": ${org.json.JSONObject.quote(body)},
      "assets": [${files.joinToString(",") { """{"name":"KoLib-${tag.removePrefix("v")}-$it.apk","size":${1000 + it.length},"browser_download_url":"https://github.com/Talilard1/KoLib/releases/download/$tag/KoLib-${tag.removePrefix("v")}-$it.apk"}""" }}]
    }"""

    @Test
    fun aLaterVersionIsToldFromAnEarlierOne() {
        assertEquals(listOf(1, 30), Updates.numbers("v1.30"))
        assertTrue(Updates.isNewer("2.0", "1.30"))
        assertTrue(Updates.isNewer("1.30", "1.4"))
        assertTrue(Updates.isNewer("2.0.1", "2.0"))
        assertTrue(Updates.isNewer("v2.1", "2.0"))
        assertFalse(Updates.isNewer("2.0", "2.0"))
        assertFalse(Updates.isNewer("2.0", "2.0.0"))
        assertFalse(Updates.isNewer("1.30", "2.0"))
        assertFalse(Updates.isNewer("nightly", "2.0"))
        // A release is later than its previews, and a later preview than an earlier one.
        assertTrue(Updates.isNewer("3.0", "3.0 preview"))
        assertTrue(Updates.isNewer("v3.0", "3.0-preview-2"))
        assertTrue(Updates.isNewer("3.0 preview 2", "3.0 preview"))
        assertFalse(Updates.isNewer("3.0 preview", "3.0"))
        assertFalse(Updates.isNewer("3.0 preview", "3.0 preview"))
        assertFalse(Updates.isNewer("2.9", "3.0 preview"))
        assertTrue(Updates.isNewer("3.1", "3.0"))
    }

    @Test
    fun theFileForTheDeviceIsTheOneMadeForItsProcessor() {
        val arm = Updates.parse(release(), listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("2.1", arm.version)
        assertEquals("KoLib-2.1-arm64-v8a.apk", arm.apkName)
        assertEquals("https://github.com/Talilard1/KoLib/releases/download/v2.1/KoLib-2.1-arm64-v8a.apk", arm.apkUrl)
        assertEquals(1009L, arm.apkBytes)
        assertEquals("https://github.com/Talilard1/KoLib/releases/tag/v2.1", arm.page)
        // x86 isn't x86_64, either way round.
        assertEquals("KoLib-2.1-x86.apk", Updates.parse(release(), listOf("x86"))!!.apkName)
        assertEquals("KoLib-2.1-x86_64.apk", Updates.parse(release(), listOf("x86_64", "x86"))!!.apkName)
        // No file made for it: the one for all devices; none of those either: no file, only the page.
        assertEquals("KoLib-2.1-universal.apk", Updates.parse(release(), listOf("riscv64"))!!.apkName)
        val none = Updates.parse(release(files = listOf("arm64-v8a", "x86")), listOf("riscv64"))!!
        assertNull(none.apkUrl)
        // A file from anywhere but KoLib's own releases isn't taken.
        val elsewhere = release().replace("https://github.com/Talilard1/KoLib/releases/download/", "https://example.org/download/")
        assertNull(Updates.parse(elsewhere, listOf("arm64-v8a"))!!.apkUrl)
        assertNull(Updates.parse("""{"tag_name":"latest","assets":[]}""", listOf("arm64-v8a")))
    }

    @Test
    fun whatsNewIsShownAsPlainLines() {
        val notes = Updates.plainNotes("### **First public release.**\r\n\r\n\r\n\r\n- **Faster** look-up\n* See [the page](https://example.org) and `code`\n\nMost phones: _KoLib-1.30-arm64-v8a.apk_\n![shot](a.png)")
        assertEquals("First public release.\n\n•  Faster look-up\n•  See the page and code\n\nMost phones: KoLib-1.30-arm64-v8a.apk", notes)
    }

    private fun updater(
        latest: () -> String = { release() }, installed: String = "2.0", body: ByteArray = ByteArray(1009) { 7 },
        verify: (File) -> String? = { null }, installedFiles: ArrayList<File> = ArrayList(), alone: Boolean = true, code: Int = 200,
    ) = Updater(
        context, scope, installed = installed, abis = listOf("arm64-v8a"), fetch = { latest() },
        open = { _, _ -> ModelStore.Answer(code, body.inputStream()) }, verify = verify, install = { installedFiles += it }, canLookAlone = alone,
    )

    private fun wait(u: Updater, until: (Updater.State) -> Boolean): Updater.State {
        val end = System.currentTimeMillis() + 10_000
        while (!until(u.state.value) && System.currentTimeMillis() < end) Thread.sleep(10)
        return u.state.value
    }

    /** Waits until Android has been handed [count] files, and the work of handing them over is done. */
    private fun handedOver(handed: List<File>, count: Int) {
        val end = System.currentTimeMillis() + 10_000
        while (handed.size < count && System.currentTimeMillis() < end) Thread.sleep(10)
        Thread.sleep(150)
    }

    @Test
    fun aDownloadCanBeStopped() {
        // A download that never ends by itself.
        val slow = object : java.io.InputStream() {
            override fun read(): Int { Thread.sleep(20); return 7 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { Thread.sleep(20); b[off] = 7; return 1 }
        }
        val handed = ArrayList<File>()
        val u = Updater(
            context, scope, installed = "2.0", abis = listOf("arm64-v8a"), fetch = { release() },
            open = { _, _ -> ModelStore.Answer(200, slow) }, verify = { null }, install = { handed += it }, canLookAlone = true,
        )
        u.lookNow()
        val found = wait(u) { it is Updater.State.Available } as Updater.State.Available
        u.update(found.release)
        val going = wait(u) { it is Updater.State.Downloading && it.bytes > 0 } as Updater.State.Downloading
        assertEquals(1009L, going.total)
        // It can't be closed while it's downloading; stopped, it's on offer again and nothing was installed.
        u.dismiss()
        assertTrue(u.state.value is Updater.State.Downloading)
        u.stop()
        assertTrue(wait(u) { it is Updater.State.Available } is Updater.State.Available)
        assertTrue(handed.isEmpty())
    }

    @Test
    fun aNewerVersionIsOfferedAndTheSameOneIsnt() {
        val u = updater()
        u.lookNow()
        val found = wait(u) { it is Updater.State.Available } as Updater.State.Available
        assertEquals("2.1", found.release.version)
        // Not now: nothing showing; it's offered again when it's next looked for.
        u.dismiss()
        assertEquals(Updater.State.Idle, u.state.value)
        // With the newest already: says so when asked, and says nothing when it looked by itself.
        val same = updater(installed = "2.1")
        same.lookNow()
        assertEquals(Updater.State.UpToDate, wait(same) { it == Updater.State.UpToDate })
        val quiet = updater(installed = "2.1")
        quiet.lookIfDue()
        Thread.sleep(300)
        assertEquals(Updater.State.Idle, quiet.state.value)
    }

    @Test
    fun itLooksByItselfOnceADayUnlessToldNotTo() {
        var asked = 0
        val u = updater(latest = { asked++; release() })
        u.lookIfDue(now = 1_000_000_000_000)
        assertTrue(wait(u) { it is Updater.State.Available } is Updater.State.Available)
        assertEquals(1, asked)
        u.dismiss()
        // An hour later: not again yet. The next day: again.
        u.lookIfDue(now = 1_000_000_000_000 + 3_600_000)
        Thread.sleep(200)
        assertEquals(1, asked)
        u.lookIfDue(now = 1_000_000_000_000 + 24 * 3_600_000L)
        wait(u) { it is Updater.State.Available }
        assertEquals(2, asked)
        // Skipped: not offered again by itself, but found when asked for.
        u.skip((u.state.value as Updater.State.Available).release)
        u.lookIfDue(now = 1_000_000_000_000 + 49 * 3_600_000L)
        Thread.sleep(300)
        assertEquals(Updater.State.Idle, u.state.value)
        u.lookNow()
        assertTrue(wait(u) { it is Updater.State.Available } is Updater.State.Available)
        u.dismiss()
        // Turned off: it doesn't look. (Nor where it isn't allowed to by itself.)
        assertEquals(4, asked)
        u.auto = false
        u.lookIfDue(now = 1_000_000_000_000 + 100 * 3_600_000L)
        Thread.sleep(200)
        assertEquals(4, asked)
        // (The choice is kept: it's still off for the next run of the app.)
        assertFalse(updater().auto)
        val barred = updater(latest = { asked++; release() }, alone = false)
        barred.auto = true
        barred.lookIfDue(now = 2_000_000_000_000)
        Thread.sleep(200)
        assertEquals(4, asked)
    }

    @Test
    fun whenGitHubCantBeReachedItSaysSoOnlyWhenAsked() {
        val asked = updater(latest = { throw java.io.IOException("no network") })
        asked.lookNow()
        assertEquals(Updater.State.Unreachable, wait(asked) { it == Updater.State.Unreachable })
        val quiet = updater(latest = { throw java.io.IOException("no network") })
        quiet.lookIfDue()
        Thread.sleep(300)
        assertEquals(Updater.State.Idle, quiet.state.value)
    }

    @Test
    fun anUpdateIsDownloadedCheckedAndHandedToAndroid() {
        val handed = ArrayList<File>()
        val checked = ArrayList<Long>()
        val u = updater(verify = { checked += it.length(); null }, installedFiles = handed)
        u.lookNow()
        val found = wait(u) { it is Updater.State.Available } as Updater.State.Available
        u.update(found.release)
        val installing = wait(u) { it is Updater.State.Installing }
        assertTrue(installing is Updater.State.Installing)
        handedOver(handed, 1)
        assertEquals(listOf(1009L), checked)
        assertEquals("KoLib-2.1.apk", handed.single().name)
        assertEquals(1009L, handed.single().length())
        // It can't be closed while Android has it. Turned down there, it's on offer again; failed, it says why.
        u.dismiss()
        assertTrue(u.state.value is Updater.State.Installing)
        u.installResult(PackageInstaller.STATUS_FAILURE_ABORTED, null)
        assertTrue(u.state.value is Updater.State.Available)
        u.update(found.release)
        wait(u) { it is Updater.State.Installing }
        handedOver(handed, 2)
        u.installResult(PackageInstaller.STATUS_FAILURE_STORAGE, "not enough room")
        assertEquals("The update couldn't be installed: not enough room", (u.state.value as Updater.State.Failed).message)
    }

    @Test
    fun aDownloadThatIsntAKoLibUpdateIsntInstalled() {
        val handed = ArrayList<File>()
        val u = updater(verify = { "That download isn't a KoLib update, so it wasn't installed." }, installedFiles = handed)
        u.lookNow()
        val found = wait(u) { it is Updater.State.Available } as Updater.State.Available
        u.update(found.release)
        val failed = wait(u) { it is Updater.State.Failed } as Updater.State.Failed
        assertEquals("That download isn't a KoLib update, so it wasn't installed.", failed.message)
        assertTrue(handed.isEmpty())
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
        // A file cut short, or an error from the site: not installed either.
        val short = updater(body = ByteArray(10), installedFiles = handed)
        short.lookNow()
        short.update((wait(short) { it is Updater.State.Available } as Updater.State.Available).release)
        assertEquals("The download didn't come through whole. Try again.", (wait(short) { it is Updater.State.Failed } as Updater.State.Failed).message)
        val missing = updater(code = 404, installedFiles = handed)
        missing.lookNow()
        missing.update((wait(missing) { it is Updater.State.Available } as Updater.State.Available).release)
        assertEquals("The download failed (error 404).", (wait(missing) { it is Updater.State.Failed } as Updater.State.Failed).message)
        assertTrue(handed.isEmpty())
        // A real check of a file that isn't an app at all.
        val junk = File(context.cacheDir, "junk.apk").apply { writeText("not an app") }
        assertEquals("That download isn't a KoLib update, so it wasn't installed.", Updater.verifyApk(context, junk))
    }
}
