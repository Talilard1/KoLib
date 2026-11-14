package dev.kobolibrary.app.reader

import dev.kobolibrary.app.tr
import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView

/** What the reader adds to the text-selection toolbar. */
enum class SelectionAction(val id: Int, val label: String, val order: Int) {
    HIGHLIGHT(0x6B4C0001, tr("Highlight"), 0),
    NOTE(0x6B4C0002, tr("Note"), 0),
    LOOK_UP(0x6B4C0003, tr("Look up"), 0),
    SEARCH(0x6B4C0004, tr("Search in book"), 200),
}

/**
 * The reader's WebView. When text is selected, the toolbar Android shows (Copy, Share, Translate and
 * any dictionary apps) gets the reader's own actions first: Highlight, Note, Look up, and Search in
 * book further along.
 */
class ReaderWebView(context: Context) : WebView(context) {
    /** Runs one of the reader's actions; the toolbar closes once it's done. Null leaves the toolbar alone. */
    var onSelectionAction: ((SelectionAction, ActionMode) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val act = onSelectionAction ?: return super.startActionMode(callback, type)
        return super.startActionMode(Wrapped(callback, act), type)
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = startActionMode(callback, ActionMode.TYPE_PRIMARY)

    private class Wrapped(
        private val inner: ActionMode.Callback,
        private val act: (SelectionAction, ActionMode) -> Unit,
    ) : ActionMode.Callback2() {
        private fun addOurs(menu: Menu) {
            for (a in SelectionAction.entries) {
                if (menu.findItem(a.id) != null) continue
                menu.add(GROUP, a.id, a.order, a.label).setShowAsAction(
                    if (a.order == 0) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER,
                )
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val ok = inner.onCreateActionMode(mode, menu)
            addOurs(menu)
            return ok
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            inner.onPrepareActionMode(mode, menu)
            addOurs(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val ours = SelectionAction.entries.firstOrNull { it.id == item.itemId && item.groupId == GROUP }
            if (ours != null) {
                act(ours, mode)
                return true
            }
            return inner.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode) = inner.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (inner is ActionMode.Callback2) inner.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    companion object {
        private const val GROUP = 0x6B4C0000
    }
}
