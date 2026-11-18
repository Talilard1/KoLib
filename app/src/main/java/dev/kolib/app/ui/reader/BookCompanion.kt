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
import dev.kolib.app.ui.components.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What's been asked and answered about the book while it's open: kept when the panel is closed and opened again. */
class CompanionState {
    /** A question and its answer as it's written. */
    class Turn(val question: String) {
        val answer = MutableStateFlow("")
        val failed = MutableStateFlow<String?>(null)
    }

    val turns = mutableStateListOf<Turn>()
    /** The conversation as the model is given it. */
    val history = ArrayList<AiMessage>()

    val summary = MutableStateFlow("")
    /** What the model is doing for the summary ("Reading part 2 of 3…"), or null when it's doing nothing. */
    val stage = MutableStateFlow<String?>(null)
    val stageFraction = MutableStateFlow(0f)
    val summaryFailed = MutableStateFlow<String?>(null)
    /** Which chapter [summary] is of, and whether it's of the whole of it. */
    var summaryOf: Pair<Int, Boolean>? = null

    /** How much of the book has been read for the question being answered (0 to 1); null when none is. */
    val reading = MutableStateFlow<Float?>(null)
    var tab by mutableIntStateOf(0)
}

/**
 * The reading companion: a summary of the chapter the reader is in, and questions about the book answered from
 * what's been read so far. Both come from the AI model the user has set up.
 */
@Composable
fun BookCompanionSheet(controller: ReaderController, state: CompanionState, language: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hub = (context.applicationContext as KoLibApp).ai
    val ai by hub.settings.collectAsState()
    val files by hub.filesChanged.collectAsState()
    val ready = remember(ai, files) { hub.ready(ai) }
    var setup by remember { mutableStateOf(false) }
    // A window floating over the page, as big as what's in it: it grows as a summary or an answer is written.
    FloatingWindow(onDismiss, Modifier.testTag("companion-window"), maxWidth = 560.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("companion")) {
            if (!ready) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Summaries and questions"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("With an AI model set up, KoLib can summarise the chapter you're in and answer questions about the book so far, without giving away what's ahead."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { setup = true }) { Text(tr("Set up AI")) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabRow(selectedTabIndex = state.tab, modifier = Modifier.weight(1f), containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                        Tab(selected = state.tab == 0, onClick = { state.tab = 0 }, text = { Text(tr("Summary")) })
                        Tab(selected = state.tab == 1, onClick = { state.tab = 1 }, text = { Text(tr("Ask")) })
                    }
                    IconButton(onClick = { setup = true }) { Icon(Icons.Outlined.Settings, tr("AI and dictionary settings")) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Close")) }
                }
                if (state.tab == 0) SummaryTab(controller, state, hub, language) else AskTab(controller, state, hub, language)
            }
        }
    }
    if (setup) AiSetupDialog(onDismiss = { setup = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SummaryTab(controller: ReaderController, state: CompanionState, hub: AiHub, language: String?) {
    val context = LocalContext.current
    val store = (context.applicationContext as KoLibApp).summaries
    val scope = rememberCoroutineScope()
    val summary by state.summary.collectAsState()
    val stage by state.stage.collectAsState()
    val fraction by state.stageFraction.collectAsState()
    val failed by state.summaryFailed.collectAsState()
    val busy by hub.busy.collectAsState()
    var whole by remember { mutableStateOf(controller.chapterProgress >= 0.9f) }
    var job by remember { mutableStateOf<Job?>(null) }
    // A summary of another chapter, or of more or less of this one, isn't the one asked for now.
    val current = state.summaryOf == (controller.chapterIndex to whole)

    fun start() {
        val index = controller.chapterIndex
        val progress = controller.chapterProgress
        val chapter = controller.chapterTitle
        val path = controller.chapters.getOrNull(index)?.path.orEmpty()
        val all = whole
        job?.cancel()
        job = scope.launch {
            state.summaryFailed.value = null
            state.summary.value = ""
            state.summaryOf = index to all
            val paragraphs = withContext(Dispatchers.Default) {
                controller.paragraphsOf(index).let { if (all) it else BookText.upTo(it, progress) }
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
                    }
                }
            } catch (e: AiException) {
                state.summaryFailed.value = e.message
            } finally {
                state.stage.value = null
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
                TextButton(onClick = { job?.cancel() }) { Text(tr("Stop")) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.AskTab(controller: ReaderController, state: CompanionState, hub: AiHub, language: String?) {
    val scope = rememberCoroutineScope()
    val reading by state.reading.collectAsState()
    val busy by hub.busy.collectAsState()
    var question by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val asking = job?.isActive == true
    val scroll = rememberScrollState()
    // What's newest stays in view: a question just asked, and its answer as it's written.
    LaunchedEffect(state.turns.size, asking) { if (state.turns.isNotEmpty()) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }

    fun ask(text: String) {
        val q = text.trim()
        if (q.isEmpty() || job?.isActive == true) return
        question = ""
        val turn = CompanionState.Turn(q)
        state.turns += turn
        val index = controller.chapterIndex
        val progress = controller.chapterProgress
        val chapter = controller.chapterTitle
        val percent = (controller.bookProgress * 100).roundToInt().coerceIn(0, 100)
        job = scope.launch {
            state.reading.value = 0f
            try {
                val chapters = withContext(Dispatchers.Default) { controller.readSoFar(index, progress) }
                val book = BookAi.Book(controller.bookTitle, controller.bookAuthor, language)
                val (end, error) = hub.use { engine ->
                    BookAi.answer(
                        engine, state.history, q, book, chapters, chapter, percent, Prompts.languageName(L10n.language),
                        (engine as? LocalEngine)?.readSpeed,
                        onProgress = { state.reading.value = it },
                        onText = { piece ->
                            state.reading.value = null
                            turn.answer.update { it + piece }
                        },
                    ) to engine.lastError
                }
                if (end == AiEnd.FAILED) turn.failed.value = error ?: tr("The model couldn't answer.")
                else if (end == AiEnd.TOO_LONG) turn.failed.value = tr("That's more than the model can take in at once. Try a shorter question.")
            } catch (e: AiException) {
                turn.failed.value = e.message
            } finally {
                state.reading.value = null
                job = null
            }
        }
    }

    // The conversation is the part that grows; the box to ask in rides under it.
    Column(Modifier.weight(1f, fill = false).grows().verticalScroll(scroll).fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            tr("Ask about the book. It answers from what you've read so far, so it won't give away what's ahead."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
        if (state.turns.isEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                listOf(tr("What has happened so far?"), tr("Who are the people in this chapter?"), tr("Where is this taking place?")).forEach { s ->
                    AssistChip(onClick = { ask(s) }, label = { Text(s) })
                }
            }
        }
        state.turns.forEachIndexed { i, turn ->
            val answer by turn.answer.collectAsState()
            val failed by turn.failed.collectAsState()
            val last = i == state.turns.size - 1
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    turn.question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = 420.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 24.dp)) {
                if (answer.isNotBlank()) SelectionContainer {
                    StreamingText(cleanAnswer(answer).replace("**", ""), last && asking, Modifier.testTag("companion-answer"), style = MaterialTheme.typography.bodyLarge)
                }
                else if (last && asking && failed == null) {
                    val r = reading
                    Text(
                        if (busy == AiHub.State.OPENING) tr("Getting {0} ready…", hub.name()) else tr("Reading the book…"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (r != null && r > 0f && r < 1f) LinearProgressIndicator(progress = { r }, modifier = Modifier.width(180.dp).padding(top = 6.dp))
                    else LinearProgressIndicator(Modifier.width(180.dp).padding(top = 6.dp))
                }
                failed?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = question, onValueChange = { question = it }, placeholder = { Text(tr("Ask about the book")) },
            modifier = Modifier.weight(1f).testTag("companion-question"), maxLines = 3, shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { ask(question) }),
        )
        if (asking) IconButton(onClick = { job?.cancel() }) { Icon(Icons.Filled.Stop, tr("Stop")) }
            else IconButton(onClick = { ask(question) }, enabled = question.isNotBlank(), modifier = Modifier.testTag("companion-send")) { Icon(Icons.AutoMirrored.Filled.Send, tr("Ask")) }
    }
}
