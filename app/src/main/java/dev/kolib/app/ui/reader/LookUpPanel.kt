package dev.kolib.app.ui.reader

import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.kolib.app.KoLibApp
import dev.kolib.app.L10n
import dev.kolib.app.ai.AiEnd
import dev.kolib.app.ai.AiException
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.BookAi
import dev.kolib.app.ai.BookText
import dev.kolib.app.ai.Prompts
import dev.kolib.app.ai.cleanAnswer
import dev.kolib.app.dict.DictResult
import dev.kolib.app.dict.Wiktionary
import dev.kolib.app.reader.LookUp
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.tr
import dev.kolib.app.ui.ai.AiSetupDialog
import dev.kolib.app.ui.components.InAppWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.ui.text.font.FontWeight
import dev.kolib.app.ui.components.PointerCard
import dev.kolib.app.ui.components.StreamingText
import dev.kolib.app.ui.components.grows
import kotlinx.coroutines.delay

/** What the dictionary has said so far about the words being looked up. */
private sealed interface Dict {
    data object Off : Dict
    data object Looking : Dict
    data object None : Dict
    data object Unreachable : Dict
    data class Found(val result: DictResult) : Dict
}

/**
 * Looking words up without leaving the book, in a window floating by them: what they mean (from the AI model, if one is
 * set up), with what they mean in the sentence they're in a tap away; their dictionary entry (Wiktionary); and Google,
 * Wikipedia and the dictionary apps installed for more. With an app chosen to always look things up in, that app
 * opens straight away instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookUpSheet(controller: ReaderController, settings: ReaderSettings, onSettings: (ReaderSettings) -> Unit, language: String?) {
    val text = controller.lookUpText ?: return
    val context = LocalContext.current
    val apps = remember { LookUp.apps(context) }
    val chosen = apps.firstOrNull { it.key == settings.dictionary }
    if (chosen != null) {
        LaunchedEffect(text) {
            LookUp.open(context, chosen, text)
            controller.lookUpText = null
        }
        return
    }
    val hub = (context.applicationContext as KoLibApp).ai
    val ai by hub.settings.collectAsState()
    val files by hub.filesChanged.collectAsState()
    val word = remember(text) { text.replace(Regex("\\s+"), " ").trim() }
    val phrase = remember(word) { BookAi.isPhrase(word) }
    var setup by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<Pair<String, String>?>(null) }
    var always by remember { mutableStateOf(false) }

    var dict by remember(word) { mutableStateOf<Dict>(Dict.Off) }
    LaunchedEffect(word, ai.dictionary) {
        if (!ai.dictionary || Wiktionary.candidates(word).isEmpty()) {
            dict = Dict.Off
            return@LaunchedEffect
        }
        dict = Dict.Looking
        dict = withContext(Dispatchers.IO) {
            try {
                Wiktionary.lookUp(word, language)?.let { Dict.Found(it) } ?: Dict.None
            } catch (e: Exception) {
                Dict.Unreachable
            }
        }
    }

    val ready = remember(ai, files) { hub.ready(ai) }
    val said = remember(word, ai.source) { MutableStateFlow("") }
    val saidNow by said.collectAsState()
    var working by remember(word, ai.source) { mutableStateOf(false) }
    var failed by remember(word, ai.source) { mutableStateOf<String?>(null) }
    var seconds by remember(word, ai.source) { mutableStateOf<Float?>(null) }
    val busy by hub.busy.collectAsState()
    LaunchedEffect(word, ai.source, ready) {
        if (!ready) return@LaunchedEffect
        working = true
        failed = null
        val started = System.nanoTime()
        try {
            val sentence = BookText.sentenceWith(controller.lookUpParagraph, word)
            val book = BookAi.Book(controller.bookTitle, controller.bookAuthor, language)
            val (end, error) = hub.use { engine ->
                BookAi.explain(engine, word, sentence, book, Prompts.languageName(L10n.language), {}) { piece -> said.update { it + piece } } to engine.lastError
            }
            if (end == AiEnd.FAILED || end == AiEnd.TOO_LONG) failed = error ?: tr("The model couldn't answer.")
            else seconds = (System.nanoTime() - started) / 1e9f
        } catch (e: AiException) {
            failed = e.message
        } finally {
            working = false
        }
    }

    // The time it's taking, counted while the answer is written.
    var elapsed by remember(word, ai.source) { mutableStateOf(0f) }
    LaunchedEffect(working) {
        if (!working) return@LaunchedEffect
        val from = System.nanoTime()
        while (true) {
            elapsed = (System.nanoTime() - from) / 1e9f
            delay(100)
        }
    }
    // The dictionary entry in full: asked for, when there's an answer from the model to read first.
    var dictOpen by remember(word) { mutableStateOf(false) }
    // What it means in the sentence it's in: written along with its meaning, shown when asked for.
    var hereOpen by remember(word) { mutableStateOf(false) }

    // A small card by the words looked up, pointing at them: as big as what's in it, growing as the answer is written.
    val screen = LocalConfiguration.current
    val rect = controller.lookUpRect ?: android.graphics.RectF(screen.screenWidthDp / 2f - 40f, screen.screenHeightDp * 0.22f, screen.screenWidthDp / 2f + 40f, screen.screenHeightDp * 0.22f + 24f)
    PointerCard(rect, controller.lookUpLines, onDismiss = { controller.lookUpText = null }, modifier = Modifier.testTag("look-up-card")) { close ->
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                word.take(80), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (ready && (working || seconds != null)) {
                Text(
                    tr("{0} s", "%.1f".format(seconds ?: elapsed)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp).testTag("look-up-time"),
                )
            }
            IconButton(onClick = close, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Close, tr("Close"), Modifier.size(20.dp)) }
        }

        // What it means, then the dictionary. This is the part that grows; what's under it rides its edge.
        Column(Modifier.weight(1f, fill = false).grows().verticalScroll(rememberScrollState()).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 2.dp).testTag("look-up")) {
            if (ready) {
                val looked = if (phrase) Prompts.readLookUp(saidNow) else Prompts.LookedUp(cleanAnswer(saidNow), "")
                when {
                    looked.meaning.isNotEmpty() -> StreamingText(looked.meaning, working && looked.here.isEmpty(), Modifier.testTag("look-up-ai"), style = MaterialTheme.typography.bodyLarge)
                    working -> StreamingText(
                        if (busy == AiHub.State.OPENING) tr("Getting {0} ready…", hub.name(ai)) else tr("Thinking…"), true,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // What it means where it's used: there for the asking.
                if (phrase && (looked.meaning.isNotEmpty() || looked.here.isNotEmpty())) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { hereOpen = !hereOpen }.padding(vertical = 4.dp)
                            .testTag("look-up-here-button"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tr("What it means in this sentence"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Icon(if (hereOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    if (hereOpen) when {
                        looked.here.isNotEmpty() -> StreamingText(looked.here, working, Modifier.testTag("look-up-here"), style = MaterialTheme.typography.bodyMedium)
                        working -> StreamingText(tr("Thinking…"), true, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                failed?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            // Its dictionary entry: the gist when that's all there is, all of it when asked for.
            if (dict != Dict.Off && (!ready || dictOpen)) {
                if (ready) Label(tr("Dictionary"), top = 12)
                when (val d = dict) {
                    Dict.Off -> {}
                    Dict.Looking -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Dict.None -> Text(tr("No dictionary entry for “{0}”.", word.take(40)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Dict.Unreachable -> Text(tr("Couldn't reach the dictionary. Check the internet connection."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is Dict.Found -> {
                        // The gist: what it's a form of, and the first meanings.
                        val senses = d.result.entries.sumOf { it.senses.size }
                        var left = if (dictOpen) Int.MAX_VALUE else 2
                        Column(Modifier.testTag("look-up-dictionary")) {
                            d.result.form?.takeIf { d.result.formOf != null }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 2.dp))
                            }
                            for (entry in d.result.entries) {
                                if (left <= 0) break
                                if (entry.partOfSpeech.isNotBlank()) {
                                    Text(entry.partOfSpeech.lowercase(), style = MaterialTheme.typography.labelLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                                entry.senses.take(left).forEachIndexed { i, sense ->
                                    Text(if (entry.senses.size > 1) "${i + 1}. $sense" else sense, style = MaterialTheme.typography.bodyMedium)
                                }
                                left -= entry.senses.size
                            }
                        }
                        val page = d.result.formOf ?: d.result.word
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            if (!dictOpen && senses > 2) {
                                Text(
                                    tr("More"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { dictOpen = true }.padding(vertical = 6.dp).padding(end = 16.dp).testTag("look-up-more"),
                                )
                            }
                            Text(
                                tr("From Wiktionary"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { web = "Wiktionary" to Wiktionary.page(page) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (!ready) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { setup = true }) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("What it means in this sentence"), style = MaterialTheme.typography.labelLarge)
                        Text(tr("An AI model on this device can tell you."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { setup = true }) { Text(tr("Set up")) }
                }
            }
        }

        // More, elsewhere: small buttons in a row that slides when there are many.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ready && dict != Dict.Off) {
                Pill(tr("Dictionary"), Icons.AutoMirrored.Outlined.MenuBook, chosen = dictOpen, modifier = Modifier.testTag("look-up-dictionary-button")) { dictOpen = !dictOpen }
            }
            Pill("Google", Icons.Outlined.Search, modifier = Modifier.testTag("look-up-google")) {
                web = "Google" to "https://www.google.com/search?q=" + Uri.encode(LookUp.defineQuery(text))
            }
            Pill(tr("Wikipedia"), Icons.Outlined.Language) {
                val lang = language?.substringBefore('-')?.lowercase()?.takeIf { it.length in 2..3 } ?: "en"
                web = tr("Wikipedia") to "https://$lang.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(word)
            }
            apps.forEach { app ->
                Pill(app.label, picture = app.icon) {
                    if (always) onSettings(settings.copy(dictionary = app.key))
                    LookUp.open(context, app, text)
                    controller.lookUpText = null
                }
            }
            IconButton(onClick = { setup = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.Settings, tr("AI and dictionary settings"), Modifier.size(20.dp)) }
        }
        if (apps.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(start = 6.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = always, onCheckedChange = { always = it })
                Text(tr("Always open the app I choose"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (setup) AiSetupDialog(onDismiss = { setup = false })
    web?.let { (title, url) -> InAppWeb(title, url) { web = null } }
}

@Composable
private fun Label(text: String, top: Int = 0) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = top.dp, bottom = 2.dp))
}

/** One of the small buttons along the bottom of the card. */
@Composable
private fun Pill(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, picture: android.graphics.drawable.Drawable? = null,
    chosen: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, shape = CircleShape, modifier = modifier,
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.height(32.dp).padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                icon != null -> Icon(icon, null, Modifier.size(16.dp))
                picture != null -> Image(picture.toBitmap(64, 64).asImageBitmap(), null, Modifier.size(16.dp))
            }
            if (icon != null || picture != null) Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
