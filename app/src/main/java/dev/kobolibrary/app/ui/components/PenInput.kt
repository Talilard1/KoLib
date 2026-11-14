package dev.kobolibrary.app.ui.components

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import dev.kobolibrary.app.log.AppLog

/**
 * Makes a tap with a pen on a text box open the keyboard, as it does in other apps, while the
 * tablet isn't set to take handwriting in text boxes (Samsung's "S Pen to text" switched off, say).
 *
 * On Android 14 and later, Compose's text boxes treat a pen that moves a pixel or two while it's
 * down as the start of handwriting, without asking whether the keyboard takes handwriting at all.
 * When it doesn't, nothing is written and the tap is lost: the box doesn't open the keyboard, so
 * it takes another tap or two. Android's own text boxes ask first, and this asks for Compose's:
 * while handwriting isn't available, no movement of the pen counts as writing, so a pen tap is an
 * ordinary tap. When handwriting is on, text boxes work as Compose made them.
 *
 * It covers [content] in this window. A dialog is a window of its own, with settings of its own,
 * which is why the app's dialogs are this file's [AlertDialog].
 */
@Composable
fun PenTapsOpenTheKeyboard(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val handwriting = LocalHandwriting.current ?: remember(context) { SystemHandwriting(context) }
    PenTapsOpenTheKeyboard(handwriting, content)
}

/** Whether the keyboard takes handwriting in text boxes. */
internal fun interface Handwriting {
    fun available(): Boolean
}

/** The answer the window that opened a dialog uses, so the dialog doesn't have to ask again. */
private val LocalHandwriting = staticCompositionLocalOf<Handwriting?> { null }

@Composable
internal fun PenTapsOpenTheKeyboard(handwriting: Handwriting, content: @Composable () -> Unit) {
    // Compose reads how far a pen must move to start handwriting from the window's view configuration.
    val window = LocalViewConfiguration.current
    val config = remember(window, handwriting) { PenTapsAreTaps(window, handwriting) }
    CompositionLocalProvider(
        LocalViewConfiguration provides config,
        LocalHandwriting provides handwriting,
        content = content,
    )
}

/** The window's own settings, except that while handwriting isn't available a pen never moves far enough to start it. */
internal class PenTapsAreTaps(
    private val window: ViewConfiguration,
    private val handwriting: Handwriting,
) : ViewConfiguration by window {
    override val handwritingSlop: Float
        get() = if (handwriting.available()) window.handwritingSlop else Float.POSITIVE_INFINITY
}

/**
 * Material's AlertDialog with its title and text under [PenTapsOpenTheKeyboard], so that pen taps
 * open the keyboard in the dialog's text boxes too. The app's dialogs use this one.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    modifier = modifier,
    dismissButton = dismissButton,
    icon = icon,
    title = penTapsOpenTheKeyboard(title),
    text = penTapsOpenTheKeyboard(text),
    shape = shape,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    tonalElevation = tonalElevation,
    properties = properties,
)

private fun penTapsOpenTheKeyboard(slot: (@Composable () -> Unit)?): (@Composable () -> Unit)? =
    slot?.let { inner -> @Composable { PenTapsOpenTheKeyboard(inner) } }

/**
 * Asks Android, at most once a second, whether the current keyboard takes handwriting in text boxes.
 * Compose checks on every movement of the pen, so the answer is kept for a moment.
 */
private class SystemHandwriting(context: Context) : Handwriting {
    private val inputMethods = context.getSystemService(InputMethodManager::class.java)
    private var answer: Boolean? = null
    private var askedAt = 0L

    override fun available(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false // Compose only offers handwriting from Android 14.
        val now = SystemClock.uptimeMillis()
        val known = answer
        if (known != null && now - askedAt < 1_000) return known
        val fresh = ask()
        if (fresh != known) AppLog.i("Pen", "Handwriting in text boxes is ${if (fresh) "on" else "off"}.")
        answer = fresh
        askedAt = now
        return fresh
    }

    @RequiresApi(34)
    private fun ask(): Boolean =
        runCatching { inputMethods?.isStylusHandwritingAvailable == true }.getOrDefault(false)
}
