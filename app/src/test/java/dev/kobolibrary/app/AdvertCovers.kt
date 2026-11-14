package dev.kobolibrary.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.kobolibrary.app.ui.cover.CoverColors
import dev.kobolibrary.app.ui.cover.CoverDesign
import dev.kobolibrary.app.ui.cover.CoverGenerator
import dev.kobolibrary.app.ui.cover.CoverStyle
import dev.kobolibrary.app.ui.cover.CoverText
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Covers for the advert made by the app's own cover designer: the ones listed in
 * /home/claude/advert/designed.tsv (file, style, theme, title, authors, series, number), written to
 * /home/claude/advert/designed. Only runs where that list is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvertCovers {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = File("/home/claude/advert")

    private fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it) }
    }

    @Test
    fun designed() {
        val list = File(root, "designed.tsv")
        assumeTrue(list.exists())
        val generator = CoverGenerator(context)
        for (line in list.readLines().filter { it.isNotBlank() && !it.startsWith("#") }) {
            val f = line.split('\t')
            val style = CoverStyle.valueOf(f[1])
            val colors = CoverColors.themes.first { it.name == f[2] }
            val text = CoverText(f[3], f[4].split('|').filter { it.isNotBlank() }, f.getOrNull(5)?.ifBlank { null }, f.getOrNull(6)?.toDoubleOrNull())
            save(generator.render(text, CoverDesign(style, colors)), File(root, "designed/${f[0]}.jpg"))
            // and in a printed book's shape, for the books in the opening
            save(generator.render(text, CoverDesign(style, colors), 1000, 1500), File(root, "designed23/${f[0]}.jpg"))
        }
    }

    /** Every style in every theme for one book, to choose from. */
    @Test
    fun matrix() {
        assumeTrue(File(root, "designed.tsv").exists())
        val generator = CoverGenerator(context)
        val text = CoverText("The Hound of the Baskervilles", listOf("Arthur Conan Doyle"), "Sherlock Holmes", 5.0)
        for (style in CoverStyle.entries) for (colors in CoverColors.themes) {
            save(generator.render(text, CoverDesign(style, colors), 536, 724), File(root, "matrix/${style.name}_${colors.name}.jpg"))
        }
    }
}
