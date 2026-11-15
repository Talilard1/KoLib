package dev.kobolibrary.app

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.library.FolderScan
import dev.kobolibrary.app.library.FoundBook
import dev.kobolibrary.app.ui.onboarding.Onboarding
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The introduction's pages, on a phone and a tablet, and in another language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntroShots {

    @get:Rule
    val compose = createComposeRule()

    private val found = FolderScan.Found("Download/Books", List(132) { FoundBook(Uri.parse("content://x/$it"), "Book $it.epub", 1000) })

    private fun shoot(name: String, page: Int, scan: FolderScan? = null, connection: Connection = Connection.Waiting(usbKoboAttached = false)) {
        // The pictures move for as long as they're shown: time is moved on by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppSurface(ThemeChoice.LIGHT) {
                Onboarding(connection, {}, {}, scan, {}, {}, {}, startAt = page)
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/intro_$name.png")
    }

    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneLanguage() = shoot("phone_1_language", 0)
    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneFolder() = shoot("phone_2_folder", 1)
    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneFolderFound() = shoot("phone_2_folder_found", 1, scan = found)
    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneKobo() = shoot("phone_3_kobo", 2, connection = Connection.Waiting(usbKoboAttached = true))
    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneCollections() = shoot("phone_4_collections", 3)
    @Test @Config(qualifiers = "w411dp-h914dp-port-xxhdpi") fun phoneDone() = shoot("phone_5_done", 4)
    @Test @Config(qualifiers = "fr-w411dp-h914dp-port-xxhdpi") fun phoneFrench() = shoot("phone_fr_collections", 3)
    @Test @Config(qualifiers = "ja-w411dp-h914dp-port-xxhdpi") fun phoneJapanese() = shoot("phone_ja_folder", 1, scan = found)

    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun tabletLanguage() = shoot("tablet_1_language", 0)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun tabletFolder() = shoot("tablet_2_folder", 1, scan = FolderScan.Adding("Download/Books", 132))
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun tabletKobo() = shoot("tablet_3_kobo", 2)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun tabletCollections() = shoot("tablet_4_collections", 3)
    @Test @Config(qualifiers = "de-w1280dp-h800dp-land-xhdpi") fun tabletGerman() = shoot("tablet_de_done", 4)
}
