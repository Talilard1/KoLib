package dev.kolib.app.ui.textedit

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import dev.kolib.app.tr
import dev.kolib.app.ui.tour.TourPlaces
import dev.kolib.app.ui.tour.TourStop

/** The names the editor's parts go by in its tour. */
internal object EditorPlace {
    const val PAGE = "page"
    const val CHAPTERS = "chapters"
    const val CHAPTER_NAME = "chapter-name"
    const val TOOLBAR = "toolbar"
    const val BOLD = "bold"
    const val ITALIC = "italic"
    const val TEXT_STYLES = "text-styles"
    const val INSERT = "insert"
    const val HEADING = "heading"
    const val ALIGN = "align"
    const val SCENE_BREAK = "scene-break"
    const val PICTURE = "picture"
    const val SPLIT = "split"
    const val SELECT = "select"
    const val COPIED = "copied"
    const val CODE = "code"
    const val UNDO = "undo"
    const val REDO = "redo"
    const val FIND = "find"
    const val CLEANUPS = "cleanups"
    const val PREVIEW = "preview"
    const val SAVE = "save"
    const val MORE = "more"
}

/**
 * The editor's tour: the page, the chapters, the row of buttons over the page from left to right, then the
 * bar along the top. A stop whose part isn't on this screen is passed over: the list of chapters has a stop
 * where it's beside the page (a tablet) and the chapter's name has one where it isn't (a phone); Find,
 * Clean-ups and Preview have one where they're buttons, and are named in the menu's stop where they're in it
 * ([toolsInMenu]). The row of buttons scrolls ([toolbar]) to bring a stop's buttons into view; [edge] is how
 * far in from its start they're brought, in pixels.
 */
internal fun editorTourStops(places: TourPlaces, toolbar: ScrollState, toolsInMenu: Boolean, edge: Float): List<TourStop> {
    fun inToolbar(vararg names: String, title: String, text: String): TourStop {
        val list = names.toList()
        return TourStop(list, title, text, reveal = {
            val row = places.rectOf(listOf(EditorPlace.TOOLBAR))
            val buttons = places.rectOf(list)
            if (row != null && buttons != null && (buttons.left < row.left + edge || buttons.right > row.right - edge)) {
                toolbar.animateScrollBy(buttons.left - row.left - edge)
            }
        })
    }
    return listOf(
        TourStop(listOf(EditorPlace.PAGE), tr("Edit the text"), tr("Tap in the chapter and type. Nothing changes in your book until you tap Save.")),
        TourStop(listOf(EditorPlace.CHAPTERS), tr("Chapters"), tr("Tap a chapter to open it. Drag one to move it, or tap ⋮ to rename, split, join or delete it.")),
        inToolbar(EditorPlace.CHAPTER_NAME, title = tr("Chapters"), text = tr("Tap the chapter’s name to see them all. There you can move, rename, split, join and delete chapters.")),
        inToolbar(
            EditorPlace.BOLD, EditorPlace.ITALIC, EditorPlace.TEXT_STYLES, EditorPlace.HEADING, EditorPlace.ALIGN, title = tr("Style"),
            text = tr("Select some words to make them bold or italic; the A button has underline and more. Headings, lists, quotes and alignment change the whole paragraph."),
        ),
        inToolbar(
            EditorPlace.INSERT, EditorPlace.SPLIT, title = tr("Add and split"),
            text = tr("Add a link, footnote, table, picture, scene break or special character where the cursor is, or split the chapter in two there."),
        ),
        inToolbar(
            EditorPlace.SELECT, title = tr("Select and copy"),
            text = tr("Select a paragraph, the whole chapter, or everything before or after the cursor. Copy, cut and paste are here too."),
        ),
        TourStop(listOf(EditorPlace.COPIED), tr("Copied sections"), tr("Chapters you copy in the chapter list are kept here, ready to add to this book or another.")),
        TourStop(listOf(EditorPlace.CODE), tr("Code"), tr("Switch to the chapter’s HTML when you need to fix something by hand.")),
        TourStop(listOf(EditorPlace.UNDO, EditorPlace.REDO), tr("Undo and redo"), tr("Every change can be undone, even a deleted chapter, until you close the editor.")),
        TourStop(
            listOf(EditorPlace.FIND, EditorPlace.CLEANUPS, EditorPlace.PREVIEW), tr("Find, clean up, preview"),
            tr("Find and replace across the whole book, fix common problems in one go, and see your changes in the reader before you save."),
        ),
        TourStop(listOf(EditorPlace.SAVE), tr("Save"), tr("Saves your changes to the book. KoLib checks the book first, and keeps the original text in case you want it back.")),
        TourStop(
            listOf(EditorPlace.MORE), tr("More"),
            if (toolsInMenu) tr("Find & replace, Clean-ups and Preview in reader are here. So are Check for problems, Restore original text, and this tour.")
            else tr("Check the book for problems, restore its original text, or see this tour again."),
        ),
    )
}
