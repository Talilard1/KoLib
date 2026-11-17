@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kolib.app.ui.edit

import dev.kolib.app.tr
import dev.kolib.app.ui.components.GoldButton
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.kolib.app.epub.HtmlText
import dev.kolib.app.log.AppLog

private data class FormatState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val bullets: Boolean = false,
    val numbers: Boolean = false,
)

/** Talks to the page in the editor. Calls arrive on a background thread. */
private class EditorBridge(
    private val initial: String,
    private val onChange: (String) -> Unit,
    private val onFormat: (FormatState) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun initialHtml(): String = initial

    @JavascriptInterface
    fun changed(html: String) {
        main.post { onChange(html) }
    }

    @JavascriptInterface
    fun state(bold: Boolean, italic: Boolean, underline: Boolean, bullets: Boolean, numbers: Boolean) {
        main.post { onFormat(FormatState(bold, italic, underline, bullets, numbers)) }
    }
}

private fun css(c: Color): String {
    val argb = c.toArgb()
    return "rgba(%d,%d,%d,%.3f)".format((argb shr 16) and 0xff, (argb shr 8) and 0xff, argb and 0xff, ((argb ushr 24) and 0xff) / 255f)
}

private fun editorPage(text: Color, hint: Color, placeholder: String) = """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<style>
html,body{margin:0;padding:0;background:transparent;min-height:100%}
body{font-family:sans-serif;font-size:17px;line-height:1.55;color:${css(text)};-webkit-text-size-adjust:100%}
#ed{min-height:60vh;box-sizing:border-box;padding:18px 20px 45vh 20px;outline:none;overflow-wrap:break-word}
#ed:empty:before{content:attr(data-placeholder);color:${css(hint)};pointer-events:none}
p{margin:0 0 .8em 0} ul,ol{margin:0 0 .8em 0;padding-left:1.6em} li{margin:0 0 .2em 0}
blockquote{margin:0 0 .8em 0;padding-left:1em;border-left:3px solid ${css(hint)}}
</style></head>
<body><div id="ed" contenteditable="true" data-placeholder="${placeholder.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")}"></div>
<script>
var ed = document.getElementById('ed');
document.execCommand('defaultParagraphSeparator', false, 'p');
ed.innerHTML = KL.initialHtml();
function send() { KL.changed(ed.innerHTML); }
function st() {
  try {
    KL.state(document.queryCommandState('bold'), document.queryCommandState('italic'),
      document.queryCommandState('underline'), document.queryCommandState('insertUnorderedList'),
      document.queryCommandState('insertOrderedList'));
  } catch (e) {}
}
ed.addEventListener('input', function () { send(); st(); });
ed.addEventListener('paste', function (e) {
  e.preventDefault();
  var t = (e.clipboardData || window.clipboardData).getData('text/plain');
  document.execCommand('insertText', false, t);
});
ed.addEventListener('drop', function (e) { e.preventDefault(); });
document.addEventListener('selectionchange', st);
function cmd(c) { ed.focus(); document.execCommand(c, false, null); send(); st(); }
</script></body></html>
""".trimIndent()

/**
 * A full-screen editor for the book's description, with bold, italic, underline and lists.
 * Pasted text arrives as plain text, so formatting from web pages doesn't come along.
 * [onDone] gets clean HTML, or null when nothing was changed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DescriptionEditor(initialHtml: String, bookTitle: String, onDone: (String?) -> Unit, onCancel: () -> Unit) {
    val start = remember { HtmlText.sanitize(initialHtml) }
    var html by remember { mutableStateOf(start) }
    var changed by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(FormatState()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewFailed by remember { mutableStateOf(false) }
    var plain by remember { mutableStateOf(runCatching { AnnotatedString.fromHtml(start.replace("</p>", "</p>\n")).text.trim() }.getOrDefault("")) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val tryCancel = { if (changed) confirmDiscard = true else onCancel() }
    BackHandler { tryCancel() }

    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.outline

    fun run(command: String) {
        webView?.evaluateJavascript("cmd('$command')", null)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = tryCancel) { Icon(Icons.Filled.Close, tr("Cancel")) } },
                title = {
                    Column {
                        Text(tr("Description"))
                        Text(bookTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                actions = {
                    GoldButton(
                        onClick = {
                            when {
                                !changed -> onDone(null)
                                webViewFailed -> onDone(HtmlText.fromPlainText(plain))
                                else -> onDone(HtmlText.sanitize(html))
                            }
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text(tr("Done")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!webViewFailed) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconToggleButton(checked = format.bold, onCheckedChange = { run("bold") }) { Icon(Icons.Filled.FormatBold, tr("Bold")) }
                        IconToggleButton(checked = format.italic, onCheckedChange = { run("italic") }) { Icon(Icons.Filled.FormatItalic, tr("Italic")) }
                        IconToggleButton(checked = format.underline, onCheckedChange = { run("underline") }) { Icon(Icons.Filled.FormatUnderlined, tr("Underline")) }
                        VerticalDivider(Modifier.padding(horizontal = 6.dp).height(28.dp))
                        IconToggleButton(checked = format.bullets, onCheckedChange = { run("insertUnorderedList") }) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, tr("Bulleted list"))
                        }
                        IconToggleButton(checked = format.numbers, onCheckedChange = { run("insertOrderedList") }) {
                            Icon(Icons.Filled.FormatListNumbered, tr("Numbered list"))
                        }
                        VerticalDivider(Modifier.padding(horizontal = 6.dp).height(28.dp))
                        IconButton(onClick = { run("removeFormat") }) { Icon(Icons.Filled.FormatClear, tr("Remove formatting from the selected text")) }
                        IconButton(onClick = { run("undo") }) { Icon(Icons.AutoMirrored.Filled.Undo, tr("Undo")) }
                        IconButton(onClick = { run("redo") }) { Icon(Icons.AutoMirrored.Filled.Redo, tr("Redo")) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (webViewFailed) {
                        Box(Modifier.padding(8.dp)) {
                            OutlinedTextField(
                                value = plain,
                                onValueChange = {
                                    plain = it
                                    changed = true
                                },
                                placeholder = { Text(tr("What the book is about…")) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        AndroidView(
                            factory = { context ->
                                try {
                                    WebView(context).apply {
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        settings.javaScriptEnabled = true
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                        settings.blockNetworkLoads = true
                                        settings.setSupportZoom(false)
                                        webViewClient = object : WebViewClient() {
                                            // Never leave the editor page.
                                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
                                        }
                                        addJavascriptInterface(
                                            EditorBridge(
                                                start,
                                                onChange = {
                                                    html = it
                                                    changed = true
                                                },
                                                onFormat = { format = it },
                                            ),
                                            "KL",
                                        )
                                        loadDataWithBaseURL(null, editorPage(textColor, hintColor, tr("What the book is about…")), "text/html", "utf-8", null)
                                        webView = this
                                    }
                                } catch (e: Exception) {
                                    // No working WebView on this tablet: fall back to plain text.
                                    AppLog.w("Edit", "The description editor couldn't start; using plain text", e)
                                    webViewFailed = true
                                    android.view.View(context)
                                }
                            },
                            onRelease = { view ->
                                if (view is WebView) {
                                    view.removeJavascriptInterface("KL")
                                    view.destroy()
                                }
                                webView = null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    tr("Pasted text is added without its formatting."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("Discard changes to the description?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onCancel()
                }) { Text(tr("Discard")) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Keep editing")) } },
        )
    }
}
