package dev.kobolibrary.app.reader

import android.view.KeyEvent

/** The open reader's handler for hardware keys (volume buttons turn pages), set while it's on screen. */
object ReaderKeys {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null
}
