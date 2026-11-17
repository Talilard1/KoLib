package dev.kolib.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import dev.kolib.app.tr

/** Puts [text] on the clipboard. Android 13 and newer show that it was copied; older ones get [said]. */
fun copyToClipboard(context: Context, text: String, said: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
}

/** The address of a web search for [text]. */
fun webSearchAddress(text: String): Uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(text.trim()))

/** Looks [text] up on the internet, in the device's own browser. */
fun searchTheInternet(context: Context, text: String) {
    val open = Intent(Intent.ACTION_VIEW, webSearchAddress(text)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(open) }
        .onFailure { Toast.makeText(context, tr("There's no browser to open it in."), Toast.LENGTH_SHORT).show() }
}

/** A search field with [value] as a phrase: `field:"value"`. */
private fun phrase(field: String, value: String) = field + ":\"" + value.replace('"', ' ').trim() + "\""

/** What finds a series' books in a search: `series:"Name"`. */
fun seriesSearch(series: String): String = phrase("series", series)

/** What finds a book by its title in a search: `title:"Title"`. */
fun titleSearch(title: String): String = phrase("title", title)

/** What finds the books by any of [authors] in a search: `author:"A" or author:"B"`; null if there are none. */
fun authorSearch(authors: List<String>): String? =
    authors.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(tr("Unknown"), ignoreCase = true) }.distinct()
        .takeIf { it.isNotEmpty() }?.joinToString(" or ") { phrase("author", it) }

/**
 * A book's title, author or series in its details, all three alike. A tap copies it ([copy], saying [copied] where
 * Android doesn't); holding it offers to look for it in KoLib ([inApp], a search for the tab it's in, by [onSearch]) or on
 * the internet ([web]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailText(
    text: String,
    style: TextStyle,
    copied: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    copy: String = text,
    inApp: String? = null,
    onSearch: ((String) -> Unit)? = null,
    web: String = copy,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text,
            style = style,
            color = color,
            textAlign = textAlign,
            // (Text set in the middle is tapped anywhere across.)
            modifier = (if (textAlign == TextAlign.Center) Modifier.fillMaxWidth() else Modifier).combinedClickable(
                onClickLabel = tr("Copy"),
                onLongClickLabel = tr("Search"),
                onLongClick = { menu = true },
                onClick = { copyToClipboard(context, copy, copied) },
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (inApp != null && onSearch != null) {
                DropdownMenuItem(
                    text = { Text(tr("Search in KoLib")) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    onClick = {
                        menu = false
                        onSearch(inApp)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(tr("Search the internet")) },
                leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
                onClick = {
                    menu = false
                    searchTheInternet(context, web)
                },
            )
        }
    }
}
