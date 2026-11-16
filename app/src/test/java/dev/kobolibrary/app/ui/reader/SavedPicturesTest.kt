package dev.kobolibrary.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedPicturesTest {

    private fun bytes(vararg start: Int) = ByteArray(16).also { b -> start.forEachIndexed { i, v -> b[i] = v.toByte() } }
    private fun bytes(start: String, at: Int = 0) = ByteArray(16).also { b -> start.forEachIndexed { i, c -> b[at + i] = c.code.toByte() } }

    @Test
    fun picturesAreSavedAsWhatTheyAre() {
        assertEquals("image/jpeg" to "jpg", SavedPictures.kindOf(bytes(0xFF, 0xD8, 0xFF)))
        assertEquals("image/png" to "png", SavedPictures.kindOf(bytes(0x89, 'P'.code, 'N'.code, 'G'.code)))
        assertEquals("image/gif" to "gif", SavedPictures.kindOf(bytes("GIF89a")))
        assertEquals("image/webp" to "webp", SavedPictures.kindOf(bytes("RIFF").also { b -> "WEBP".forEachIndexed { i, c -> b[8 + i] = c.code.toByte() } }))
        // A drawing (an SVG) isn't one a gallery shows.
        assertNull(SavedPictures.kindOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertNull(SavedPictures.kindOf(ByteArray(0)))
    }

    @Test
    fun theyreNamedForTheBookAndThePicture() {
        assertEquals("The Garden - rose.jpg", SavedPictures.nameFor("The Garden", "OEBPS/images/rose.jpeg", "jpg"))
        assertEquals("Who Goes There - map 1.png", SavedPictures.nameFor("Who Goes There?", "OEBPS/images/map:1.png", "png"))
        assertEquals("Picture.png", SavedPictures.nameFor("", "", "png"))
    }
}
