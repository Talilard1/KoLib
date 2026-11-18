package dev.kolib.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.L10n
import dev.kolib.app.ai.AiEnd
import dev.kolib.app.ai.AiException
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.AiMessage
import dev.kolib.app.ai.BookAi
import dev.kolib.app.ai.ChatStore
import dev.kolib.app.ai.LibraryAi
import dev.kolib.app.ai.LocalEngine
import dev.kolib.app.ai.Prompts
import dev.kolib.app.ai.SavedChat
import dev.kolib.app.ai.SavedTurn
import dev.kolib.app.ai.cleanAnswer
import dev.kolib.app.library.LibraryBook
import dev.kolib.app.reader.ReadHere
import dev.kolib.app.reader.ReaderController
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Button
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.FloatingWindow
import dev.kolib.app.ui.components.StreamingText
import dev.kolib.app.ui.components.grows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Opens the chat with the model from wherever it can be asked for (the library's bar); null where it can't. */
val LocalOpenAiChat = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Where a question is asked from, which decides what the model is told with it. */
sealed interface ChatPlace {
    /** What a turn asked here is marked with: a book's title, or nothing for the library. */
    val where: String

    /** In a book being read: answered from what's been read of it so far. */
    class Book(val controller: ReaderController, val language: String?) : ChatPlace {
        override val where: String get() = controller.bookTitle
    }

    /** In the library: answered with the list of the books in it. */
    class Library(val books: List<LibraryBook>, val reading: Map<String, ReadHere> = emptyMap()) : ChatPlace {
        override val where: String get() = ""
    }
}

/**
 * The chat with the model: one conversation, the same wherever it's opened from (the library, a book), which
 * goes on while the app is open and is kept on the device to come back to. Other chats kept before can be
 * opened again, or deleted.
 */
class ChatSession(
    private val store: ChatStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val prefs: android.content.SharedPreferences? = null,
) {
    /** A question and its answer as it's written. */
    class Turn(val question: String, val where: String, answer: String = "") {
        val answer = MutableStateFlow(answer)
        val failed = MutableStateFlow<String?>(null)
    }

    var id by mutableStateOf(newId())
        private set
    val turns = mutableStateListOf<Turn>()
    val saved = MutableStateFlow(store.list())

    /** How much of the book has been read for the question being answered (0 to 1); null when none is. */
    val reading = MutableStateFlow<Float?>(null)
    var job by mutableStateOf<Job?>(null)
        private set
    val asking: Boolean get() = job?.isActive == true

    private var readsBook by mutableStateOf(prefs?.getBoolean("chat_reads_book", true) ?: true)

    /**
     * Asked in a book, whether the model is shown what's been read of it: on, it answers from the book and
     * can't give away what's ahead; off, it answers from what it knows, without the wait while it reads.
     */
    var readBook: Boolean
        get() = readsBook
        set(value) {
            readsBook = value
            prefs?.edit()?.putBoolean("chat_reads_book", value)?.apply()
        }

    /** The turn whose question is being changed (the next thing sent takes its place, and what came after it goes). */
    var editing by mutableStateOf<Int?>(null)

    private fun newId() = java.util.UUID.randomUUID().toString()

    /** A new, empty chat: the one there was stays among the kept ones. */
    fun startNew() {
        // Not while it's answering: an answer is only stopped with its stop button.
        if (asking) return
        turns.clear()
        editing = null
        id = newId()
    }

    /** Goes back to a chat that was kept. */
    fun open(saved: String) {
        if (asking) return
        val kept = store.load(saved) ?: return
        editing = null
        turns.clear()
        turns.addAll(kept.map { Turn(it.question, it.where, it.answer) })
        id = saved
    }

    fun delete(saved: String) {
        if (saved == id && asking) return
        store.delete(saved)
        if (saved == id) startNew()
        this.saved.value = store.list()
    }

    private fun keep() {
        store.save(id, turns.filter { it.answer.value.isNotBlank() }.map { SavedTurn(it.question, cleanAnswer(it.answer.value), it.where) })
        saved.value = store.list()
    }

    /** What's been asked in the same place before, and answered: what the model is reminded of. */
    private fun history(where: String, before: Turn): MutableList<AiMessage> {
        val out = ArrayList<AiMessage>()
        for (t in turns) {
            if (t === before) break
            val a = cleanAnswer(t.answer.value)
            if (t.where != where || a.isBlank()) continue
            out += AiMessage.user("Question: " + t.question)
            out += AiMessage.assistant(a)
        }
        return out
    }

    /** Asks [text] from [place]. When a question is being changed, this takes its place and what followed it goes. */
    fun ask(hub: AiHub, place: ChatPlace, text: String) {
        val q = text.trim()
        if (q.isEmpty() || asking) return
        editing?.let { at ->
            if (at in turns.indices) turns.removeRange(at, turns.size)
            editing = null
        }
        val turn = Turn(q, place.where)
        turns += turn
        job = scope.launch {
            reading.value = 0f
            try {
                val answerIn = Prompts.languageName(L10n.language)
                val instructions = hub.settings.value.chatPrompt
                val onText = { piece: String ->
                    reading.value = null
                    turn.answer.update { it + piece }
                }
                val (end, error) = when (place) {
                    is ChatPlace.Book -> if (!readBook) {
                        val c = place.controller
                        hub.use { engine ->
                            LibraryAi.plain(engine, history(place.where, turn), q, c.bookTitle, c.bookAuthor, answerIn, onProgress = { reading.value = it }, onText = onText, instructions = instructions) to engine.lastError
                        }
                    } else {
                        val c = place.controller
                        val index = c.chapterIndex
                        val progress = c.chapterProgress
                        val chapter = c.chapterTitle
                        val percent = (c.bookProgress * 100).roundToInt().coerceIn(0, 100)
                        val chapters = withContext(Dispatchers.Default) { c.readSoFar(index, progress) }
                        val book = BookAi.Book(c.bookTitle, c.bookAuthor, place.language, c.bookDescription)
                        hub.use { engine ->
                            BookAi.answer(
                                engine, history(place.where, turn), q, book, chapters, chapter, percent, answerIn,
                                (engine as? LocalEngine)?.readSpeed, onProgress = { reading.value = it }, onText = onText, instructions = instructions,
                            ) to engine.lastError
                        }
                    }
                    is ChatPlace.Library -> {
                        val shelf = withContext(Dispatchers.Default) { place.books.map { shelved(it, place.reading[it.id]) } }
                        hub.use { engine ->
                            LibraryAi.answer(engine, history(place.where, turn), q, shelf, answerIn, onProgress = { reading.value = it }, onText = onText, instructions = instructions) to engine.lastError
                        }
                    }
                }
                if (end == AiEnd.FAILED) turn.failed.value = error ?: tr("The model couldn't answer.")
                else if (end == AiEnd.TOO_LONG) turn.failed.value = tr("That's more than the model can take in at once. Try a shorter question.")
            } catch (e: AiException) {
                turn.failed.value = e.message
            } finally {
                reading.value = null
                job = null
                keep()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun shelved(b: LibraryBook, here: ReadHere?): LibraryAi.Shelved = LibraryAi.Shelved(
        title = b.meta.title,
        authors = b.meta.authors.joinToString(", "),
        series = b.meta.series?.let { s -> b.meta.seriesIndex?.let { n -> "$s #" + (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) } ?: s },
        tags = b.meta.tags,
        state = when {
            here == null -> null
            here.finished -> "finished"
            here.started -> "${here.percent}% read"
            else -> "not started"
        },
    )
}

/** The chat with the model in a window of its own, floating over whatever's showing: opened from the library. */
@Composable
fun AiChatWindow(place: ChatPlace, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KoLibApp
    val hub = app.ai
    val ai by hub.settings.collectAsState()
    val files by hub.filesChanged.collectAsState()
    val ready = remember(ai, files) { hub.ready(ai) }
    var setup by remember { mutableStateOf(false) }
    FloatingWindow(onDismiss, Modifier.testTag("ai-chat-window"), maxWidth = 560.dp) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Text(tr("AI"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
            IconButton(onClick = { setup = true }) { Icon(Icons.Outlined.Settings, tr("AI and dictionary settings")) }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Close")) }
        }
        if (!ready) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 20.dp)) {
                Text(
                    tr("With an AI model set up, you can ask about your library here, and about a book while you read it."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { setup = true }) { Text(tr("Set up AI")) }
            }
        } else {
            ChatPane(app.chat, place, hub)
        }
    }
    if (setup) AiSetupDialog(onDismiss = { setup = false })
}

/**
 * The chat itself: its name with the buttons for a new chat and the kept ones, then the conversation with
 * the box to ask in under it, or the list of kept chats. The conversation is the part that grows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ChatPane(session: ChatSession, place: ChatPlace, hub: AiHub) {
    val reading by session.reading.collectAsState()
    val busy by hub.busy.collectAsState()
    val saved by session.saved.collectAsState()
    var question by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf(false) }
    val asking = session.asking
    val inBook = place is ChatPlace.Book

    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (listing) tr("Saved chats") else session.turns.firstOrNull()?.question ?: tr("New chat"),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { session.startNew(); listing = false; question = "" }, enabled = (session.turns.isNotEmpty() || listing) && !asking, modifier = Modifier.testTag("chat-new")) {
            Icon(Icons.Filled.Add, tr("New chat"))
        }
        IconButton(onClick = { listing = !listing }, modifier = Modifier.testTag("chat-saved")) {
            Icon(Icons.Outlined.History, tr("Saved chats"), tint = if (listing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (listing) {
        Column(Modifier.weight(1f, fill = false).grows().verticalScroll(rememberScrollState()).fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            if (saved.isEmpty()) {
                Text(tr("No saved chats yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            saved.forEach { chat ->
                val current = chat.id == session.id
                // While it's answering, the chat it's answering in stays: it's stopped with its stop button, not by leaving it.
                SavedChatRow(
                    chat, current, canOpen = !asking || current, canDelete = !(asking && current),
                    onOpen = { if (!current) session.open(chat.id); listing = false }, onDelete = { session.delete(chat.id) },
                )
            }
        }
        return
    }

    val scroll = rememberScrollState()
    // What's newest stays in view: a question just asked, and its answer as it's written.
    LaunchedEffect(session.turns.size, asking) { if (session.turns.isNotEmpty()) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    Column(Modifier.weight(1f, fill = false).grows().verticalScroll(scroll).fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            when {
                inBook && session.readBook -> tr("Ask about the book. It answers from what you've read so far, so it won't give away what's ahead.")
                inBook -> tr("Ask anything. It isn't reading the book, so it answers from what it knows.")
                else -> tr("Ask about your books, or anything else. It knows what's in your library.")
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 10.dp),
        )
        if (session.turns.isEmpty()) {
            val starters = if (inBook) listOf(tr("What has happened so far?"), tr("Who are the people in this chapter?"), tr("Where is this taking place?"))
            else listOf(tr("What should I read next?"), tr("Which of my books are in a series?"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                starters.forEach { s -> AssistChip(onClick = { session.ask(hub, place, s) }, label = { Text(s) }) }
            }
        }
        var lastWhere: String? = null
        session.turns.forEachIndexed { i, turn ->
            val answer by turn.answer.collectAsState()
            val failed by turn.failed.collectAsState()
            val last = i == session.turns.size - 1
            // Where it was asked, when that's somewhere else than the one before.
            if (turn.where != lastWhere && (lastWhere != null || turn.where != place.where)) {
                Text(
                    turn.where.ifEmpty { tr("Library") }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp),
                )
            }
            lastWhere = turn.where
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                // A question can be changed and asked again: the chat carries on from there.
                IconButton(
                    onClick = { session.editing = i; question = turn.question }, enabled = !asking,
                    modifier = Modifier.size(36.dp).testTag("chat-edit-$i"),
                ) { Icon(Icons.Outlined.Edit, tr("Edit this message"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    turn.question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .background(if (session.editing == i) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 24.dp)) {
                if (answer.isNotBlank()) SelectionContainer {
                    StreamingText(cleanAnswer(answer).replace("**", ""), last && asking, Modifier.testTag("companion-answer"), style = MaterialTheme.typography.bodyLarge)
                }
                else if (last && asking && failed == null) {
                    val r = reading
                    Text(
                        when {
                            busy == AiHub.State.OPENING -> tr("Getting {0} ready…", hub.name())
                            inBook && session.readBook -> tr("Reading the book…")
                            else -> tr("Thinking…")
                        },
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
    session.editing?.let {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Changing your message: what came after it will go."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { session.editing = null; question = "" }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, tr("Cancel"), Modifier.size(16.dp)) }
        }
    }
    if (inBook) {
        // Whether it's shown the book: on, its answers come from what's been read; off, from what it knows.
        androidx.compose.material3.FilterChip(
            selected = session.readBook, onClick = { session.readBook = !session.readBook }, enabled = !asking,
            label = { Text(tr("Reads the book")) },
            leadingIcon = if (session.readBook) ({ Icon(androidx.compose.material.icons.Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
            modifier = Modifier.padding(start = 20.dp).testTag("chat-reads-book"),
        )
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = question, onValueChange = { question = it },
            placeholder = { Text(if (inBook) tr("Ask about the book") else tr("Ask about your books")) },
            modifier = Modifier.weight(1f).testTag("companion-question"), maxLines = 3, shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { session.ask(hub, place, question); question = "" }),
        )
        if (asking) IconButton(onClick = { session.stop() }) { Icon(Icons.Filled.Stop, tr("Stop")) }
        else IconButton(onClick = { session.ask(hub, place, question); question = "" }, enabled = question.isNotBlank(), modifier = Modifier.testTag("companion-send")) {
            Icon(Icons.AutoMirrored.Filled.Send, tr("Ask"))
        }
    }
}

/** A chat that's been kept: what it's called and when it was last added to; tapped, it's opened again. */
@Composable
private fun SavedChatRow(chat: SavedChat, current: Boolean, canOpen: Boolean, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (current) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = canOpen, onClick = onOpen).padding(start = 12.dp, top = 6.dp, bottom = 6.dp).testTag("chat-${chat.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tr("{0} · {1}", Fmt.dateTime(chat.changedAt).orEmpty(), if (chat.questions == 1) tr("1 question") else tr("{0} questions", chat.questions)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.testTag("chat-delete-${chat.id}")) { Icon(Icons.Outlined.Delete, tr("Delete this chat")) }
    }
}
