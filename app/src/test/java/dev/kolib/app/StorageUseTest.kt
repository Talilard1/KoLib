package dev.kolib.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Where KoLib's space goes: each kind of thing it keeps, found by going through its folders. */
class StorageUseTest {
    private val data: File = Files.createTempDirectory("storage-use").toFile()

    @After
    fun tearDown() {
        data.deleteRecursively()
    }

    private fun file(path: String, bytes: Int) = File(data, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun eachKindIsCountedOnceAndWhatsLeftIsOther() {
        file("files/library/book1/book.epub", 1000)
        file("files/library/book1/cover.jpg", 100)
        file("files/library/book1/ocr/page1.txt", 50)
        file("files/library/comic1/comic.cbz", 2000)
        file("files/library/comic1/cover.jpg", 200)
        file("files/library/not-a-book/thing", 7)
        file("files/ocr/tessdata/eng.traineddata", 300)
        file("files/models/small.gguf", 400)
        file("files/ai/chats/1.json", 40)
        file("files/fonts/serif.ttf", 10)
        file("files/backups/KoboReader.sqlite", 20)
        file("cache/updates/KoLib-3.5.apk", 30)
        file("cache/pictures/page1.jpg", 60)
        file("app_webview/Default/Cache/data_0", 15)
        file("databases/library.db", 5)
        val comic = mapOf("book1" to false, "comic1" to true)
        val use = StorageUse.measure(
            data, File(data, "files"), File(data, "cache"), File(data, "files/library"), { comic[it] },
            app = 90_000, total = 1_000_000, free = 400_000,
        )
        val parts = use.parts.toMap()
        assertEquals(1000L, parts[StorageUse.Kind.BOOKS])
        assertEquals(2000L, parts[StorageUse.Kind.COMICS])
        assertEquals(300L, parts[StorageUse.Kind.COVERS])
        assertEquals(350L, parts[StorageUse.Kind.TEXT])
        assertEquals(440L, parts[StorageUse.Kind.AI])
        assertEquals(10L, parts[StorageUse.Kind.FONTS])
        assertEquals(20L, parts[StorageUse.Kind.BACKUPS])
        assertEquals(30L, parts[StorageUse.Kind.UPDATES])
        // What's kept for a while: the cache but for the updates, and the web view's.
        assertEquals(75L, parts[StorageUse.Kind.TEMP])
        // A folder in the library that isn't a book, and the database: the rest.
        assertEquals(12L, parts[StorageUse.Kind.OTHER])
        assertEquals(90_000L, parts[StorageUse.Kind.APP])
        assertEquals(1000L + 2000 + 300 + 350 + 440 + 10 + 20 + 30 + 75 + 12 + 90_000, use.kolib)
    }
}
