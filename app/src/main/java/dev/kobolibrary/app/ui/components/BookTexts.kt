package dev.kobolibrary.app.ui.components

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
import androidx.compose.material.icons.outlined.ContentCopy
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
import dev.kobolibrary.app.tr

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

/** What finds a series' books in a search: `series:"Name"`. */
fun seriesSearch(series: String): String = "series:\"" + series.replace('"', ' ').trim() + "\""

/** A book's title in its details. A tap copies it; holding it offers to look it up on the internet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookTitleText(title: String, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            title,
            style = style,
            textAlign = textAlign,
            // (A title set in the middle is tapped anywhere across.)
            modifier = (if (textAlign == TextAlign.Center) Modifier.fillMaxWidth() else Modifier).combinedClickable(
                onClickLabel = tr("Copy the title"),
                onLongClickLabel = tr("More"),
                onLongClick = { menu = true },
                onClick = { copyToClipboard(context, title, tr("Title copied")) },
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(tr("Search the internet")) },
                leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
                onClick = {
                    menu = false
                    searchTheInternet(context, title)
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Copy")) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                onClick = {
                    menu = false
                    copyToClipboard(context, title, tr("Title copied"))
                },
            )
        }
    }
}

/**
 * The series a book is in, in its details ([label]: with its number). A tap shows the series' books, by [onSeries] with
 * the series' name; holding it copies the name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesText(label: String, series: String, style: TextStyle, color: Color, onSeries: ((String) -> Unit)?, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val context = LocalContext.current
    Text(
        label,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier.combinedClickable(
            onClickLabel = tr("Show the books in this series"),
            onLongClickLabel = tr("Copy the series"),
            onLongClick = { copyToClipboard(context, series, tr("Series copied")) },
            onClick = { onSeries?.invoke(series) },
        ),
    )
}
