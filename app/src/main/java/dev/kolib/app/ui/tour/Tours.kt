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
    const val BOOK_READER = "book-reader"
    const val COMIC_READER = "comic-reader"
    /** Kept with the tours seen: the sample books were offered (added to a library with none, the first time KoLib starts). */
    const val SAMPLES = "samples"
    /** The first time a book's details show (its screen on a phone, beside the shelf on a tablet). */
    const val BOOK_DETAILS = "book-details"
    /** The first time the Comics tab shows. */
    const val COMICS = "comics"
    /** The first time a series of comics is opened. */
    const val COMIC_SERIES = "comic-series"

    fun library() = listOf(
        TourStop(listOf("library-shelf"), tr("Your library"), tr("Tap a book to see it, read it or send it to your Kobo. Hold one to choose several at once.")),
        TourStop(listOf("library-tabs"), tr("Books, collections and comics"), tr("Switch between your books, your collections and your comics. You can swipe between them too.")),
        TourStop(listOf("library-search"), tr("Find a book"), tr("Search titles, authors, series and tags. Tap ? to see all the ways you can search.")),
        TourStop(
            listOf("library-display"), tr("Filter, sort and display"),
            tr("Choose which books show, their order, and how they look: covers or a list, and how many in a row."),
        ),
        TourStop(listOf("library-menu"), tr("Select and sync"), tr("Choose several books at once, or sync your reading with the Kobo.")),
        TourStop(listOf("library-add"), tr("Add books"), tr("Add books and comics from this device, a folder or your Kobo. You can write a new book here too.")),
        TourStop(
            listOf("library-sync"), tr("Sync reading with the Kobo"),
            tr("Your place, bookmarks, highlights and notes, between KoLib and your Kobo. You choose which way each goes."),
        ),
        TourStop(listOf("nav-kobo"), tr("Your Kobo"), tr("Plug in your Kobo to see what's on it, send it books and back it up.")),
        TourStop(listOf("nav-devices"), tr("Your other devices"), tr("Send books to KoLib on your other phones and tablets over Wi-Fi, or to your Kobo without the cable.")),
        TourStop(listOf("nav-settings"), tr("More"), tr("Settings, reading stats, these tours again, the Kobo's backups, the log and KoLib's details.")),
    )

    fun bookDetails() = listOf(
        TourStop(
            listOf("book-more"), tr("More for this book"),
            tr("Mark it as finished or unread, send it to another device, save it as a file, duplicate it or split it. You can remove it from the library here too."),
        ),
        TourStop(
            listOf("book-edit"), tr("Change it"),
            tr("Edit its details or cover, or its text: chapters, styles, links, tables and footnotes. A comic's pages are edited here too."),
        ),
    )

    fun comics() = listOf(
        TourStop(
            listOf("comics-shelf"), tr("Your comics"),
            tr("Each series is one entry, with its comics in it. Tap a series to open it, or hold one to choose several: to send them, mark them read, duplicate them or save them as files."),
        ),
        TourStop(listOf("library-add"), tr("Add comics"), tr("Add comic files from this device, or make a comic of your own from pictures.")),
    )

    fun comicSeries() = listOf(
        TourStop(listOf("comic-series-head"), tr("The series"), tr("How many comics it has and how many you haven't read. Continue opens the next one to read.")),
        TourStop(
            listOf("comic-series-menu"), tr("The series' menu"),
            tr("Mark the whole series read or unread, rename it, put its comics in another series, or remove it."),
        ),
        TourStop(
            listOf("comic-series-issue"), tr("Its comics"),
            tr("Tap a comic to read it. Hold one to choose several, to send, mark, duplicate or save them. Info in that bar has a comic's details, where you can edit its pages or split it."),
        ),
    )

    fun koboConnect() = listOf(
        TourStop(listOf("kobo-connect"), tr("Connect your Kobo"), tr("Plug it in with a USB-C cable and tap Connect on its screen. KoLib opens it here.")),
        TourStop(
            listOf("kobo-help"), tr("Backups and the log"),
            tr("KoLib backs up your Kobo's database when it connects. The backups and the log are here, even when the Kobo isn't plugged in."),
        ),
    )

    fun kobo() = listOf(
        TourStop(
            listOf("kobo-shelf"), tr("What's on your Kobo"),
            tr("Every book on your Kobo. Tap one to read it here or add it to your library, where you edit and send books. Hold one to choose several."),
        ),
        TourStop(listOf("kobo-tabs"), tr("Books, collections and About"), tr("The About tab has the Kobo's details, its free space and its backups.")),
        TourStop(
            listOf("kobo-display"), tr("Filter, sort and display"),
            tr("Choose which books show, their order, and how they look: covers or a list, and how many in a row."),
        ),
        TourStop(listOf("kobo-eject"), tr("Eject"), tr("Tap this before you unplug the Kobo, so it can add the books you've sent.")),
        TourStop(listOf("kobo-menu"), tr("More"), tr("Back up the Kobo's database now, check KoLib can write to it, or forget this Kobo.")),
    )

    fun devices() = listOf(
        TourStop(listOf("devices-me"), tr("This device"), tr("Your other devices find this one on the same Wi-Fi, or when one is on the other's hotspot.")),
        TourStop(listOf("devices-paired"), tr("Paired"), tr("Devices you've paired with. Choose books in your library and tap To device to send them.")),
        TourStop(listOf("devices-nearby"), tr("Nearby"), tr("Your other phones and tablets on the same network show here while this screen is open on them too. Tap Pair to link one.")),
        TourStop(listOf("devices-kobo-wifi"), tr("Your Kobo, over Wi-Fi"), tr("Send books to your Kobo without the cable, through its web browser.")),
        TourStop(
            listOf("devices-settings"), tr("Settings"),
            tr("Choose whether your place in a book is shared with your other devices, whether KoLib looks for them all the time, and whether they can send while KoLib is closed."),
        ),
    )

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
