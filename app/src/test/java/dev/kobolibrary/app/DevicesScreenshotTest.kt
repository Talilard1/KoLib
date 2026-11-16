package dev.kobolibrary.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kobolibrary.app.link.LinkManager
import dev.kobolibrary.app.link.LinkSettings
import dev.kobolibrary.app.link.PairedDevice
import dev.kobolibrary.app.link.Pairing
import dev.kobolibrary.app.link.Transfer
import dev.kobolibrary.app.ui.AppFrame
import dev.kobolibrary.app.ui.Section
import dev.kobolibrary.app.ui.link.ChooseDeviceDialog
import dev.kobolibrary.app.ui.link.DevicesActions
import dev.kobolibrary.app.ui.link.DevicesContent
import dev.kobolibrary.app.ui.link.DevicesView
import dev.kobolibrary.app.ui.link.PairingDialog
import dev.kobolibrary.app.ui.theme.AppSurface
import dev.kobolibrary.app.ui.theme.ThemeChoice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val TABLET = "w1280dp-h800dp-land-mdpi"
private const val TABLET_NIGHT = "w1280dp-h800dp-land-night-mdpi"
private const val PHONE = "w411dp-h891dp-port-mdpi"

/** The Devices section, and pairing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DevicesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun inLanguage() = TestLanguage.apply()

    private val actions = DevicesActions(
        pair = {}, reach = { null }, sendBooks = {}, sendFiles = {}, sendClipboard = {}, syncPlaces = {}, forget = {},
        keepPlacesInStep = {}, stayReachable = {},
    )

    private val phone = PairedDevice("phone-1", "Galaxy A72", "", 0L)
    private val other = PairedDevice("tab-2", "Old tablet", "", 0L)

    private fun view(transfers: List<Transfer> = emptyList()) = DevicesView(
        me = "Galaxy Tab S9",
        addresses = listOf("192.168.1.23"),
        port = LinkManager.PORT,
        paired = listOf(phone, other),
        nearby = listOf(
            LinkManager.Nearby("phone-1", "Galaxy A72", "192.168.1.31", LinkManager.PORT, 0L),
            LinkManager.Nearby("pc-3", "Galaxy S24", "192.168.1.40", LinkManager.PORT, 0L),
        ),
        transfers = transfers,
        settings = LinkSettings(keepPlacesInStep = true, stayReachable = false),
    )

    private fun shot(name: String, theme: ThemeChoice = ThemeChoice.SYSTEM, screen: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppSurface(theme) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        val path = "${TestLanguage.folder("build/outputs/roborazzi")}/$name.png"
        if (screen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun devicesOnTheTablet() = shot("devicesTablet") {
        AppFrame(section = Section.DEVICES, onSection = {}, koboConnected = false, onNarrow = {}, onLook = {}, devicesHere = true) {
            DevicesContent(view(listOf(Transfer(1, "Galaxy A72", "The Histories", outgoing = true, done = 45, total = 100))), actions)
        }
    }

    @Test
    @Config(qualifiers = TABLET_NIGHT)
    fun devicesOnTheTabletAtNight() = shot("devicesTabletNight", ThemeChoice.DARK) {
        AppFrame(section = Section.DEVICES, onSection = {}, koboConnected = false, onNarrow = {}, onLook = {}) {
            DevicesContent(view().copy(paired = emptyList(), nearby = view().nearby.drop(1)), actions)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun devicesOnThePhone() = shot("devicesPhone") {
        AppFrame(section = Section.DEVICES, onSection = {}, koboConnected = true, onNarrow = {}) {
            DevicesContent(view().copy(me = "Galaxy A72", paired = listOf(PairedDevice("tab-1", "Galaxy Tab S9", "", 0L)), nearby = emptyList()), actions)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun devicesWithNoNetwork() = shot("devicesNoNetwork") {
        AppFrame(section = Section.DEVICES, onSection = {}, koboConnected = false, onNarrow = {}) {
            DevicesContent(view().copy(addresses = emptyList(), paired = emptyList(), nearby = emptyList()), actions)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun connectingByAddress() {
        shot("devicesConnectByAddress", screen = false) {
            AppFrame(section = Section.DEVICES, onSection = {}, koboConnected = false, onNarrow = {}) {
                DevicesContent(view().copy(paired = emptyList(), nearby = emptyList()), actions)
            }
        }
        compose.onNodeWithText("Not found? Connect by address").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("${TestLanguage.folder("build/outputs/roborazzi")}/devicesConnectByAddress.png")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun askedToPair() = shot("pairingAsked", screen = true) {
        PairingDialog(Pairing("Galaxy A72", "482913", incoming = true)) {}
    }

    @Test
    @Config(qualifiers = PHONE)
    fun waitingForTheOtherToPair() = shot("pairingWaiting", screen = true) {
        PairingDialog(Pairing("Galaxy Tab S9", "482913", incoming = false, confirmed = true)) {}
    }

    @Test
    @Config(qualifiers = PHONE)
    fun choosingWhereToSend() = shot("chooseDevice", screen = true) {
        ChooseDeviceDialog(listOf(phone, other), setOf("phone-1"), "3 books", {}, {})
    }

    private val wifiBooks = listOf(
        dev.kobolibrary.app.kobo.WifiBook(0, "a", "The Histories", "Herodotus", "kepub", "The Histories - Herodotus.kepub.epub"),
        dev.kobolibrary.app.kobo.WifiBook(1, "b", "Dracula", "Bram Stoker", "kepub", "Dracula - Bram Stoker.kepub.epub"),
        dev.kobolibrary.app.kobo.WifiBook(2, "c", "Meditations", "Marcus Aurelius", "PDF", "Meditations - Marcus Aurelius.pdf"),
        dev.kobolibrary.app.kobo.WifiBook(3, "d", "Moby-Dick", "Herman Melville", "kepub", "Moby-Dick - Herman Melville.kepub.epub"),
    )
    private val wifiStates = mapOf(
        0 to dev.kobolibrary.app.kobo.WifiState.Sent(1_250_000),
        1 to dev.kobolibrary.app.kobo.WifiState.Sending(420_000, 1_000_000),
        2 to dev.kobolibrary.app.kobo.WifiState.Ready(8_400_000),
        3 to dev.kobolibrary.app.kobo.WifiState.Making,
    )

    @Test
    @Config(qualifiers = PHONE)
    fun theKoboOverWifiOnThePhone() = shot("koboWifiPhone") {
        dev.kobolibrary.app.ui.KoboWifiContent(wifiBooks, wifiStates, listOf("192.168.1.31"), 8080) {}
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theKoboOverWifiOnTheTablet() = shot("koboWifiTablet") {
        dev.kobolibrary.app.ui.KoboWifiContent(wifiBooks.take(2), wifiStates.mapValues { dev.kobolibrary.app.kobo.WifiState.Sent(1_000_000) }, listOf("192.168.1.23", "10.12.40.1"), 8080) {}
    }

    @Test
    @Config(qualifiers = TABLET)
    fun theKobosOwnPage() {
        // What the Kobo's browser is given, as a page this device's browser can show.
        val server = dev.kobolibrary.app.kobo.KoboWifiServer(wifiBooks, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO), make = { throw java.io.IOException() }, cover = { null })
        val html = server.page()
        java.io.File(TestLanguage.folder("build/outputs/roborazzi")).mkdirs()
        java.io.File("${TestLanguage.folder("build/outputs/roborazzi")}/koboPage.html").writeText(html)
        assert("The Histories" in html)
    }
}
