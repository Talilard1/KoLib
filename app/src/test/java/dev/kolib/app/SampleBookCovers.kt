package dev.kolib.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.kolib.app.ui.cover.CoverColors
import dev.kolib.app.ui.cover.CoverDesign
import dev.kolib.app.ui.cover.CoverGenerator
import dev.kolib.app.ui.cover.CoverGenre
import dev.kolib.app.ui.cover.CoverStyle
import dev.kolib.app.ui.cover.CoverText
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Covers for the sample books that come with KoLib, made by its own cover designer: the ones listed in
 * tools/samples/covers.tsv (file, style, theme, genre, title, authors, width, height), written to
 * tools/samples/build/covers. Only runs once that build folder has been made (see tools/samples/README.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SampleBookCovers {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun covers() {
        val home = File("../tools/samples")
        val list = File(home, "covers.tsv")
        assumeTrue(list.exists() && File(home, "build").isDirectory)
        val generator = CoverGenerator(context)
        for (line in list.readLines().filter { it.isNotBlank() && !it.startsWith("#") }) {
            val f = line.split('\t')
            val style = CoverStyle.valueOf(f[1])
            if (style.picture) continue
            val design = CoverDesign(style, CoverColors.themes.first { it.name == f[2] }, CoverGenre.valueOf(f[3]))
            val text = CoverText(f[4], f[5].split('|').filter { it.isNotBlank() }, null, null)
            val out = File(home, "build/covers/${f[0]}.jpg")
            out.parentFile?.mkdirs()
            val bitmap = generator.render(text, design, f[6].toInt(), f[7].toInt())
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
    }
}
