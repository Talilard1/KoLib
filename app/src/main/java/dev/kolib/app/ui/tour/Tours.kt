package dev.kolib.app.ui.tour

import dev.kolib.app.tr

/**
 * The tours of KoLib's screens, each given the first time its screen is opened (see ThemeSettings.toursSeen), and
 * again when they're asked for in Settings. A stop whose part isn't on the screen as it's arranged on this device
 * (a phone or a tablet) is passed over.
 */
object Tours {
    const val LIBRARY = "library"
    const val KOBO_CONNECT = "kobo-connect"
    const val KOBO = "kobo"
    const val DEVICES = "devices"
    const val SETTINGS = "settings"
    const val BOOK_READER = "book-reader"
    const val COMIC_READER = "comic-reader"
    /** Kept with the tours seen: the sample books were offered (added to a library with none, the first time KoLib starts). */
    const val SAMPLES = "samples"

    fun library() = listOf(
        TourStop(listOf("library-shelf"), tr("Your library"), tr("Tap a book to see it, read it or send it to your Kobo. Hold one to choose several at once.")),
        TourStop(listOf("library-tabs"), tr("Books, collections and comics"), tr("Switch between your books, your collections and your comics. You can swipe between them too.")),
        TourStop(listOf("library-search"), tr("Find a book"), tr("Search titles, authors, series and tags. Tap ? to see all the ways you can search.")),
        TourStop(listOf("library-menu"), tr("More"), tr("Choose books, switch between covers and a list, or open Settings and Reading stats.")),
        TourStop(listOf("library-add"), tr("Add books"), tr("Add books and comics from this device, a folder or your Kobo. You can write a new book here too.")),
        TourStop(
            listOf("library-sync"), tr("Sync reading with the Kobo"),
            tr("Your place, bookmarks, highlights and notes, between KoLib and your Kobo. You choose which way each goes."),
        ),
        TourStop(listOf("library-stats"), tr("Reading stats"), tr("See how much you read, in KoLib and on your Kobo.")),
        TourStop(listOf("nav-kobo"), tr("Your Kobo"), tr("Plug in your Kobo to see what's on it, send it books and back it up.")),
        TourStop(listOf("nav-devices"), tr("Your other devices"), tr("Send books to KoLib on your other phones and tablets over Wi-Fi, or to your Kobo without the cable.")),
        TourStop(listOf("nav-settings"), tr("Settings"), tr("Change how KoLib looks, its language, and how books are read.")),
    )

    fun koboConnect() = listOf(
        TourStop(listOf("kobo-connect"), tr("Connect your Kobo"), tr("Plug it in with a USB-C cable and tap Connect on its screen. KoLib opens it here.")),
        TourStop(
            listOf("kobo-help"), tr("Backups and the log"),
            tr("KoLib backs up your Kobo's database when it connects. The backups and the log are here, even when the Kobo isn't plugged in."),
        ),
    )

    fun kobo() = listOf(
        TourStop(listOf("kobo-shelf"), tr("What's on your Kobo"), tr("Every book on your Kobo. Tap one to see it, or hold one to choose several to add to your library or delete.")),
        TourStop(listOf("kobo-tabs"), tr("Books, collections and the device"), tr("The Device tab has the Kobo's details, its free space and its database backups.")),
        TourStop(listOf("kobo-eject"), tr("Eject"), tr("Tap this before you unplug the Kobo, so it can add the books you've sent.")),
        TourStop(listOf("kobo-menu"), tr("More"), tr("Back up the Kobo's database, see the log, open Settings, or forget this Kobo.")),
    )

    fun devices() = listOf(
        TourStop(listOf("devices-me"), tr("This device"), tr("Your other devices find this one on the same Wi-Fi, or when one is on the other's hotspot.")),
        TourStop(listOf("devices-paired"), tr("Paired"), tr("Devices you've paired with. Choose books in your library and tap To device to send them.")),
        TourStop(listOf("devices-nearby"), tr("Nearby"), tr("Your other phones and tablets with KoLib open on the same network show here. Tap Pair to link one.")),
        TourStop(listOf("devices-kobo-wifi"), tr("Your Kobo, over Wi-Fi"), tr("Send books to your Kobo without the cable, through its web browser.")),
        TourStop(
            listOf("devices-settings"), tr("Settings"),
            tr("Choose whether your place in a book is shared with your other devices, and whether they can send while KoLib is closed."),
        ),
    )

    /** The Settings window's tour: [reveal] scrolls the window to the setting named before it's lit. */
    fun settings(reveal: (String) -> (suspend () -> Unit)) = listOf(
        stop("settings-language", tr("Language"), tr("Choose the language KoLib uses."), reveal),
        stop("settings-folder", tr("Books folder"), tr("A folder KoLib adds books from. Look in it again to add the new ones."), reveal),
        stop("settings-reading", tr("Reading setup"), tr("Set the page colour, font, text size and how pages turn, on an example book."), reveal),
        stop("settings-ai", tr("AI and dictionary"), tr("Look up words, and get chapter summaries and answers about the book you're reading."), reveal),
        stop("settings-comics", tr("Comics"), tr("Give comics a tab of their own in the library, with each series as one entry."), reveal),
        stop("settings-look", tr("Look"), tr("Choose how KoLib looks. The opening animation matches the look you choose."), reveal),
        stop("settings-opening", tr("Opening animation"), tr("Turn the animation and its sound on or off, or use a lighter one on a slower device."), reveal),
        stop("settings-tours", tr("Tours"), tr("See these tours again, each the next time you open its screen."), reveal),
    )

    private fun stop(place: String, title: String, text: String, reveal: (String) -> (suspend () -> Unit)) =
        TourStop(listOf(place), title, text, reveal = reveal(place))

    fun bookReader() = listOf(
        TourStop(listOf("reader-page"), tr("Turn the page"), tr("Tap the edges of the page or swipe to turn it. Tap the middle to show or hide these bars.")),
        TourStop(listOf("reader-page"), tr("Highlights and notes"), tr("Hold a word and drag to select more, then tap Highlight, Note or Look up.")),
        TourStop(listOf("reader-search-mark"), tr("Search and bookmark"), tr("Search the whole book, or bookmark the page you're on.")),
        TourStop(listOf("reader-slider"), tr("Move through the book"), tr("Drag to go anywhere in the book. The arrows go to the chapter before or after.")),
        TourStop(listOf("reader-contents"), tr("Contents"), tr("The chapters, and your bookmarks, highlights and notes.")),
        TourStop(listOf("reader-autoscroll"), tr("Autoscroll"), tr("The text moves on by itself. Tap the middle of the page to pause it.")),
        TourStop(listOf("reader-rotation"), tr("Rotation"), tr("Keep the screen upright or sideways while you read, or let it turn.")),
        TourStop(listOf("reader-settings"), tr("Reading settings"), tr("The font, text size, margins, page colour, and how pages turn.")),
    )

    fun comicReader() = listOf(
        TourStop(
            listOf("comic-page"), tr("Turn the page"),
            tr("Tap the edges of the page or swipe to turn it. Tap the middle to show or hide these bars. Pinch or double-tap to zoom."),
        ),
        TourStop(listOf("comic-page"), tr("Hold a page"), tr("Hold a page to save it, share it, or use it as the comic's cover.")),
        TourStop(listOf("comic-slider"), tr("Move through the comic"), tr("Drag to any page. The buttons at the ends go to the comic before or after in the series.")),
        TourStop(listOf("comic-mode"), tr("Reading mode"), tr("Left to right, right to left as in manga, or one long strip. It's kept for each series.")),
        TourStop(listOf("comic-rotation"), tr("Rotation"), tr("Keep the screen upright or sideways for this series, or let it turn.")),
        TourStop(listOf("comic-crop"), tr("Crop borders"), tr("Trims the blank edges from the pages, so the art fills more of the screen.")),
        TourStop(listOf("comic-settings"), tr("Settings"), tr("How taps work, the page colours and brightness, and more.")),
    )
}
