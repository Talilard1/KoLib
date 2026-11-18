package dev.kolib.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.kolib.app.ui.components.grows
import dev.kolib.app.ui.components.StreamingText
import dev.kolib.app.ui.components.FloatingWindow
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.L10n
import dev.kolib.app.ai.AiEnd
import dev.kolib.app.ai.AiException
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.AiMessage
import dev.kolib.app.ai.BookAi
import dev.kolib.app.ai.BookText
import dev.kolib.app.ai.LocalEngine
import dev.kolib.app.ai.Prompts
import dev.kolib.app.ai.cleanAnswer
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.tr
import dev.kolib.app.ui.ai.AiSetupDialog
import dev.kolib.app.ui.ai.ChatPane
import dev.kolib.app.ui.ai.ChatPlace
import dev.kolib.app.ui.components.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The chapter's summary while the book is open: kept when the window is closed and opened again, and written
 * on whether the window is open or not. Only Stop stops it.
 */
class CompanionState {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    var job by mutableStateOf<Job?>(null)
    val summary = MutableStateFlow("")
    /** What the model is doing for the summary ("Reading part 2 of 3…"), or null when it's doing nothing. */
    val stage = MutableStateFlow<String?>(null)
    val stageFraction = MutableStateFlow(0f)
    val summaryFailed = MutableStateFlow<String?>(null)
    /** Which chapter [summary] is of, and whether it's of the whole of it. */
    var summaryOf: Pair<Int, Boolean>? = null

    var tab by mutableIntStateOf(0)
}

/**
 * The summary of the chapter the reader is in, in the AI's window: of what's been read of it, or of the whole of
 * it. It's written whether the window is open or not; only Stop stops it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.SummaryTab(controller: ReaderController, state: CompanionState, hub: AiHub, language: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as KoLibApp
    val store = app.summaries
    val scope = state.scope
    val summary by state.summary.collectAsState()
    val stage by state.stage.collectAsState()
    val fraction by state.stageFraction.collectAsState()
    val failed by state.summaryFailed.collectAsState()
    val busy by hub.busy.collectAsState()
    var whole by remember { mutableStateOf(controller.chapterProgress >= 0.9f) }
    // A summary of another chapter, or of more or less of this one, isn't the one asked for now.
    val current = state.summaryOf == (controller.chapterIndex to whole)

    fun start() {
        val index = controller.chapterIndex
        val progress = controller.chapterProgress
        val chapter = controller.chapterTitle
        val path = controller.chapters.getOrNull(index)?.path.orEmpty()
        val all = whole
        state.job?.cancel()
        state.job = scope.launch {
            state.summaryFailed.value = null
            state.summary.value = ""
            state.summaryOf = index to all
            var written: String? = null
            app.aiStarted()
            try {
            // What's been read: to the end of the page on the screen.
            val screen = if (all) null else controller.onScreen()?.text?.takeIf { it.isNotBlank() }
            val paragraphs = withContext(Dispatchers.Default) {
                controller.paragraphsOf(index).let { p -> if (all) p else screen?.let { BookText.upToScreen(p, it, progress) } ?: BookText.upTo(p, progress) }
            }
            if (paragraphs.sumOf { it.length } < 120) {
                state.summaryFailed.value = if (all) tr("There's too little text in this chapter to summarise.") else tr("You've read too little of this chapter to summarise yet.")
                return@launch
            }
            val model = hub.name()
            val key = store.key(controller.bookTitle, controller.bookAuthor, path, paragraphs.sumOf { it.length }, model, L10n.language)
            store.get(key)?.let {
                state.summary.value = it
                return@launch
            }
            state.stage.value = tr("Getting {0} ready…", model)
            state.stageFraction.value = 0f
            try {
                val book = BookAi.Book(controller.bookTitle, controller.bookAuthor, language)
                val (result, error) = hub.use { engine ->
                    BookAi.summarise(
                        engine, paragraphs, chapter, book, soFar = !all, answerIn = Prompts.languageName(L10n.language),
                        onStage = { s ->
                            state.stage.value = when {
                                s.part == 0 -> tr("Writing the summary…")
                                s.parts == 1 -> tr("Reading the chapter…")
                                else -> tr("Reading part {0} of {1}…", s.part, s.parts)
                            }
                            state.stageFraction.value = s.fraction
                        },
                        onText = { piece -> state.summary.update { it + piece } },
                    ) to engine.lastError
                }
                val (end, text) = result
                when {
                    end == AiEnd.TOO_LONG -> state.summaryFailed.value = tr("This chapter is too long for the model.")
                    end == AiEnd.FAILED -> state.summaryFailed.value = error ?: tr("The model couldn't answer.")
                    end == AiEnd.CANCELLED -> state.summary.value = ""
                    text.isNotBlank() -> {
                        state.summary.value = text
                        store.put(key, text)
                        written = text
                    }
                }
            } catch (e: AiException) {
                state.summaryFailed.value = e.message
            } finally {
                state.stage.value = null
            }
            } finally {
                app.aiFinished(tr("The summary of “{0}”", chapter.ifBlank { controller.bookTitle }), written)
            }
        }
    }

    val scroll = rememberScrollState()
    // While it's being written, what's newest stays in view.
    LaunchedEffect(stage != null) { if (stage != null) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    Column(Modifier.fillMaxWidth().weight(1f, fill = false).grows().verticalScroll(scroll).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 18.dp)) {
        Text(controller.chapterTitle.ifBlank { controller.bookTitle }, style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            FilterChip(selected = !whole, onClick = { whole = false }, label = { Text(tr("What I've read")) }, enabled = stage == null)
            FilterChip(selected = whole, onClick = { whole = true }, label = { Text(tr("The whole chapter")) }, enabled = stage == null)
        }
        Spacer(Modifier.height(10.dp))
        when {
            stage != null -> {
                Text(if (busy == AiHub.State.OPENING) tr("Getting {0} ready…", hub.name()) else stage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fraction > 0f && fraction < 1f) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                if (summary.isNotBlank()) SummaryText(summary, writing = true)
                TextButton(onClick = { state.job?.cancel() }) { Text(tr("Stop")) }
            }
            current && failed != null -> {
                Text(failed.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { start() }) { Text(tr("Try again")) }
            }
            current && summary.isNotBlank() -> {
                SummaryText(summary)
                Text(hub.name(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                Text(
                    if (whole) tr("A summary of this whole chapter. It may give away what you haven't read yet.")
                    else tr("A summary of this chapter up to where you are."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { start() }, modifier = Modifier.testTag("companion-summarise")) { Text(tr("Summarise")) }
            }
        }
    }
}

@Composable
private fun SummaryText(text: String, writing: Boolean = false) {
    val shown = cleanAnswer(text).lines().joinToString("\n") { line ->
        val t = line.trimStart()
        if (t.startsWith("- ") || t.startsWith("* ")) "•  " + t.substring(2).replace("**", "") else line.replace("**", "")
    }
    SelectionContainer { StreamingText(shown, writing, Modifier.testTag("companion-summary"), style = MaterialTheme.typography.bodyLarge) }
}
