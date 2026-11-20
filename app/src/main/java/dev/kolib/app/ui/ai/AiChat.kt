package dev.kolib.app.ui.ai

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.Eject
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

    /**
     * In a book being read: answered from what's been read of it so far and what's on the screen. Its chapter
     * summary ([companion]) and the AI's notes on it ([notes], written by [writer]) go with it, where it has them.
     */
    class Book(
        val controller: ReaderController,
        val language: String?,
        val companion: dev.kolib.app.ui.reader.CompanionState = dev.kolib.app.ui.reader.CompanionState(),
        val notes: dev.kolib.app.ai.BookNotes? = null,
        val writer: dev.kolib.app.ai.NotesWriter? = null,
    ) : ChatPlace {
        override val where: String get() = controller.bookTitle

        val book: BookAi.Book get() = BookAi.Book(controller.bookTitle, controller.bookAuthor, language, controller.bookDescription)

        /** The chapters finished so far that may have notes: in the book's order, before the one the reader is in. */
        fun finished(): List<dev.kolib.app.ai.NoteChapter> = finishedChapters(controller)

        /** The notes on the chapters before the one the reader is in, by their names, when notes are kept. */
        fun notesSoFar(): List<Pair<String, String>> {
            val n = notes?.takeIf { it.on.value } ?: return emptyList()
            return finished().mapNotNull { ch -> n.note(ch.path)?.takeIf { it.isNotBlank() }?.let { ch.title to it } }
        }
    }

    /**
     * In the library: answered with the list of the books in it, the ones on the screen first. [screen] says what
     * the library is showing (see [LibraryOnScreen]), when it's known.
     */
    class Library(
        val books: List<LibraryBook>,
        val reading: Map<String, ReadHere> = emptyMap(),
        val screen: () -> LibraryOnScreen? = { null },
    ) : ChatPlace {
        override val where: String get() = ""
    }
}

/** The chapters of the book open in [c] that the reader has finished, that may have notes: not what's in front of the book. */
fun finishedChapters(c: ReaderController): List<dev.kolib.app.ai.NoteChapter> =
    (0 until c.chapterIndex.coerceAtMost(c.chapters.size)).filter { c.chapters[it].linear }.mapNotNull { i ->
        val title = c.titleOf(i)
        if (dev.kolib.app.ai.BookText.frontMatter(title)) null else dev.kolib.app.ai.NoteChapter(i, c.chapters[i].path, title)
    }

/**
 * What the library is showing, for the AI to know: which tab, the filter, search and order chosen, the books
 * listed (by id, in order), and the book open, if one is.
 */
data class LibraryOnScreen(val describe: String, val shown: List<String>, val open: String? = null)

/**
 * The chat with the model: one conversation, the same wherever it's opened from (the library, a book), which
 * goes on while the app is open and is kept on the device to come back to. Other chats kept before can be
 * opened again, or deleted.
 */
class ChatSession(
    private val store: ChatStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val prefs: android.content.SharedPreferences? = null,
    /** Told when the model starts on a question, and when it's done with it (with its answer, unless it gave none). */
    private val onStarted: () -> Unit = {},
    private val onFinished: (question: String, answer: String?) -> Unit = { _, _ -> },
) {
    /** A question and its answer as it's written. */
    class Turn(val question: String, val where: String, answer: String = "", val picked: String? = null) {
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

    /** Words picked out on a book's page to ask about ("Ask AI"): sent with the next question, then let go of. */
    var attached by mutableStateOf<String?>(null)

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
    private fun history(where: String, before: Turn?): MutableList<AiMessage> {
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

    /** Where a book's reader is, as a question asked there is answered from: what's been read, the screen, the notes. */
    private suspend fun placeIn(place: ChatPlace.Book, selected: String?): BookAi.Place {
        val c = place.controller
        val index = c.chapterIndex
        val progress = c.chapterProgress
        val chapter = c.chapterTitle
        val percent = (c.bookProgress * 100).roundToInt().coerceIn(0, 100)
        val screen = c.onScreen()?.text?.takeIf { it.isNotBlank() }
        val notes = place.notesSoFar()
        return withContext(Dispatchers.Default) {
            BookAi.Place(
                chapters = c.readSoFar(index, progress, screen),
                chapter = chapter,
                percent = percent,
                screen = screen,
                beforeScreen = screen?.let { c.readSoFar(index, progress, it, toScreenStart = true) },
                notes = notes,
                selected = selected,
            )
        }
    }

    /** The library's books as the model is told of them: the ones on the screen first, in the order they're shown. */
    private suspend fun shelfIn(place: ChatPlace.Library): Pair<List<LibraryAi.Shelved>, String> {
        val screen = place.screen()
        return withContext(Dispatchers.Default) {
            val order = screen?.shown.orEmpty().withIndex().associate { it.value to it.index }
            val books = place.books.withIndex().sortedBy { order[it.value.id] ?: (order.size + it.index) }.map { it.value }
            val open = screen?.open?.let { id -> place.books.firstOrNull { it.id == id } }
            val describe = listOfNotNull(
                screen?.describe?.takeIf { it.isNotBlank() },
                open?.let { "The book open on the screen is “" + it.meta.title + "”" + (it.meta.authors.takeIf { a -> a.isNotEmpty() }?.let { a -> " by " + a.joinToString(", ") } ?: "") + "." },
            ).joinToString(" ")
            books.map { shelved(it, place.reading[it.id]) } to describe
        }
    }

    /** Asks [text] from [place]. When a question is being changed, this takes its place and what followed it goes. */
    fun ask(hub: AiHub, place: ChatPlace, text: String) {
        val q = text.trim()
        if (q.isEmpty() || asking) return
        editing?.let { at ->
            if (at in turns.indices) turns.removeRange(at, turns.size)
            editing = null
        }
        val words = attached?.takeIf { place is ChatPlace.Book }
        attached = null
        val turn = Turn(q, place.where, picked = words)
        turns += turn
        onStarted()
        job = scope.launch {
            reading.value = 0f
            var answered: String? = null
            try {
                val answerIn = Prompts.languageName(L10n.language)
                val instructions = hub.settings.value.instructionsNow()
                val onText = { piece: String ->
                    reading.value = null
                    turn.answer.update { it + piece }
                }
                val (end, error) = when (place) {
                    is ChatPlace.Book -> if (!readBook) {
                        val c = place.controller
                        val asked = if (words != null) q + "\n\n(About these words in the book: “" + words + "”)" else q
                        hub.use { engine ->
                            LibraryAi.plain(engine, history(place.where, turn), asked, c.bookTitle, c.bookAuthor, answerIn, instructions, onProgress = { reading.value = it }, onText = onText) to engine.lastError
                        }
                    } else {
                        val where = placeIn(place, words)
                        hub.use { engine ->
                            BookAi.answer(
                                engine, history(place.where, turn), q, place.book, where, answerIn,
                                (engine as? LocalEngine)?.readSpeed, onProgress = { reading.value = it }, instructions = instructions, onText = onText,
                            ) to engine.lastError
                        }
                    }
                    is ChatPlace.Library -> {
                        val (shelf, screen) = shelfIn(place)
                        hub.use { engine ->
                            LibraryAi.answer(engine, history(place.where, turn), q, shelf, answerIn, instructions, screen, onProgress = { reading.value = it }, onText = onText) to engine.lastError
                        }
                    }
                }
                if (end == AiEnd.FAILED) turn.failed.value = error ?: tr("The model couldn't answer.")
                else if (end == AiEnd.TOO_LONG) turn.failed.value = tr("That's more than the model can take in at once. Try a shorter question.")
                else if (end == AiEnd.DONE || end == AiEnd.LENGTH) answered = cleanAnswer(turn.answer.value).takeIf { it.isNotBlank() }
            } catch (e: AiException) {
                turn.failed.value = e.message
            } finally {
                reading.value = null
                job = null
                keep()
                onFinished(q, answered)
            }
        }
    }

    /**
     * Reads ahead what a question asked from [place] will be read with (the book's context, or the library's, and
     * the conversation so far), so that when it's asked only the question is left to read: done while the reader
     * types it. Nothing while something's being answered.
     */
    fun warm(hub: AiHub, place: ChatPlace) {
        if (asking || !hub.ready()) return
        scope.launch {
            val answerIn = Prompts.languageName(L10n.language)
            val instructions = hub.settings.value.instructionsNow()
            val history = history(place.where, null)
            when (place) {
                is ChatPlace.Book -> if (!readBook) {
                    val c = place.controller
                    hub.warm { e -> LibraryAi.plainAhead(e, history, c.bookTitle, c.bookAuthor, answerIn, instructions) }
                } else {
                    val where = placeIn(place, null)
                    hub.warm { e -> BookAi.ahead(e, history, place.book, where, answerIn, instructions) }
                }
                is ChatPlace.Library -> {
                    val (shelf, screen) = shelfIn(place)
                    hub.warm { e -> LibraryAi.ahead(e, history, shelf, screen, answerIn, instructions) }
                }
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

/**
 * The chat itself: its name with the buttons for a new chat and the kept ones, then the conversation with
 * the box to ask in under it, or the list of kept chats. The conversation is the part that grows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ChatPane(session: ChatSession, place: ChatPlace, hub: AiHub, onSettings: (() -> Unit)? = null) {
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
        // While the model's held in memory, and not answering: let go of it, to free the memory and save battery.
        val held by hub.held.collectAsState()
        if (held && !asking) {
            val context = LocalContext.current
            IconButton(
                onClick = {
                    hub.unload()
                    android.widget.Toast.makeText(context, tr("Unloaded. It loads again when you next ask it something."), android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("chat-unload"),
            ) { Icon(Icons.Outlined.Eject, tr("Unload the AI"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        InstructionsMenu(session, place, hub, asking, onSettings)
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
    // Full screen, the conversation takes all the room, with the box to ask in at the foot of it.
    val full = (LocalContext.current.applicationContext as dev.kolib.app.KoLibApp).aiPanel.full
    Column(Modifier.weight(1f, fill = full).grows().verticalScroll(scroll).fillMaxWidth().padding(horizontal = 16.dp)) {
        val presetNow = hub.settings.collectAsState().value.preset
        Text(
            when {
                inBook && session.readBook -> tr("Ask about the book. It answers from what you've read so far, so it won't give away what's ahead.")
                inBook -> tr("Ask anything. It isn't reading the book, so it answers from what it knows.")
                else -> tr("Ask about your books, or anything else. It knows what's in your library.")
            } + (presetNow?.let { " " + tr("It's using your “{0}” instructions.", it.name) } ?: ""),
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
            turn.picked?.let { words ->
                Text(
                    "“" + words.replace(Regex("\\s+"), " ") + "”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 48.dp, end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                // Held, a question can be copied, or changed and asked again: the chat carries on from there.
                HeldMenu(turn.question, "chat-question-$i", onEdit = if (asking) null else ({ session.editing = i; question = turn.question })) { held ->
                    Text(
                        turn.question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .background(if (session.editing == i) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp))
                            .then(held)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
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
    // Words picked out on the page to ask about: they go with the next question.
    session.attached?.takeIf { inBook }?.let { words ->
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(start = 12.dp).testTag("chat-attached"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "“" + words.replace(Regex("\\s+"), " ") + "”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            IconButton(onClick = { session.attached = null }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, tr("Don't ask about these words"), Modifier.size(16.dp)) }
        }
    }
    if (place is ChatPlace.Book) BookSwitches(session, place, hub, asking)
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // The AI's character, by the box: it watches what's typed, and shows what it's doing with it. A tap: the
        // characters to switch to.
        val motion = rememberMascotMotion(seed = 7)
        motion.mood = aiMood(place)
        motion.library = !inBook
        motion.listening = question.isNotBlank() && !asking
        var switching by remember { mutableStateOf(false) }
        Box {
            MascotView(
                chosenCharacter(), motion,
                Modifier.size(56.dp).clip(CircleShape).clickable(onClickLabel = tr("Change the AI's character")) { switching = true }.testTag("chat-mascot"),
            )
            CharacterMenu(switching) { switching = false }
        }
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

/**
 * In a book, under the chat: whether the AI reads the book, and whether it keeps notes on it as it's read (and, if
 * it does, only while charging), with how far the notes have got.
 */
@OptIn(ExperimentalLayoutApi::class)

/**
 * A message that, held, offers to copy it, and for the reader's own questions, to change it ([onEdit]; null while
 * that can't be done). [content] is the message, given the modifier that makes it answer to being held.
 */
@Composable
private fun HeldMenu(text: String, tag: String, onEdit: (() -> Unit)?, content: @Composable (Modifier) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val label = tr("More options")
    Box {
        content(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        open = true
                    })
                }
                .semantics { onLongClick(label) { open = true; true } }
                .testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) DropdownMenuItem(
                text = { Text(tr("Edit")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { open = false; onEdit() }, modifier = Modifier.testTag("$tag-edit"),
            )
            DropdownMenuItem(
                text = { Text(tr("Copy")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                onClick = { open = false; clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.testTag("$tag-copy"),
            )
        }
    }
}

/**
 * The chat's instructions, switched on the fly: the usual ones or a preset; the ones in use can be kept as a preset
 * from here, or changed in the AI settings.
 */
@Composable
private fun InstructionsMenu(session: ChatSession, place: ChatPlace, hub: AiHub, asking: Boolean, onSettings: (() -> Unit)?) {
    val settings by hub.settings.collectAsState()
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val now = settings.preset
    val character = chosenCharacter()
    val everyone = allCharacters().map { it.id }
    fun use(preset: dev.kolib.app.ai.PromptPreset?) {
        hub.settingsStore.set(settings.using(preset))
        open = false
        // Read ahead again, with the new instructions.
        if (!asking) session.warm(hub, place)
    }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("chat-instructions")) {
            Icon(
                Icons.Outlined.TheaterComedy, tr("Chat instructions"),
                tint = if (settings.chatPrompt.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                tr("Chat instructions"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            DropdownMenuItem(
                text = { Text(tr("{0}'s own", character.nameIn(settings))) }, onClick = { use(null) },
                leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(18.dp).alpha(if (settings.chatPrompt.isEmpty()) 1f else 0f)) },
                trailingIcon = { MascotIcon(character, BotMood.IDLE, Modifier.size(20.dp)) },
                modifier = Modifier.testTag("chat-preset-usual"),
            )
            settings.presets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { use(p) },
                    leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(18.dp).alpha(if (p == now) 1f else 0f)) },
                    // Who's using it: the characters take their instructions with them.
                    trailingIcon = { Heads(settings.usersOf(p, everyone)) },
                    modifier = Modifier.testTag("chat-preset-${p.name}"),
                )
            }
            if (settings.chatPrompt.isNotEmpty() && now == null) DropdownMenuItem(
                text = { Text(tr("Your own (not saved)")) }, onClick = { open = false }, enabled = false,
                leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) },
            )
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr("Save as a preset")) }, onClick = { open = false; naming = true },
                modifier = Modifier.testTag("chat-preset-save"),
            )
            if (onSettings != null) DropdownMenuItem(text = { Text(tr("Change the instructions")) }, onClick = { open = false; onSettings() })
        }
    }
    if (naming) PresetNameDialog(now?.name.orEmpty(), onDismiss = { naming = false }) { name ->
        hub.settingsStore.set(settings.withPreset(name, settings.chatPrompt.ifEmpty { character.ownInstructions(settings) }))
        naming = false
    }
}

@Composable
private fun BookSwitches(session: ChatSession, place: ChatPlace.Book, hub: AiHub, asking: Boolean) {
    val notes = place.notes
    val writer = place.writer
    val notesOn = notes?.on?.collectAsState()?.value == true
    val written = notes?.written?.collectAsState()?.value ?: 0
    val status = writer?.status?.collectAsState()?.value
    val settings by hub.settings.collectAsState()
    @Composable
    fun Switch(on: Boolean, label: String, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) = androidx.compose.material3.FilterChip(
        selected = on, onClick = { onChange(!on) }, enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (on) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
        modifier = Modifier.testTag(tag),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Whether it's shown the book: on, its answers come from what's been read; off, from what it knows.
        Switch(session.readBook, tr("Reads the book"), "chat-reads-book", enabled = !asking) { session.readBook = it }
        if (notes != null) {
            Switch(notesOn, tr("Book notes"), "chat-notes") { notes.setOn(it) }
            if (notesOn) Switch(settings.notesOnCharge, tr("Only while charging"), "chat-notes-charging") { hub.settingsStore.set(settings.copy(notesOnCharge = it)) }
        }
    }
    if (notes != null && writer != null && notesOn) {
        val (done, of) = remember(written, place.controller.chapterIndex) { writer.progress() }
        val line = when (status) {
            is dev.kolib.app.ai.NotesWriter.Status.Writing -> (if (status.chapter.isBlank()) tr("Writing a note on a chapter…") else tr("Writing a note on “{0}”…", status.chapter))
            is dev.kolib.app.ai.NotesWriter.Status.Waiting -> when (status.why) {
                dev.kolib.app.ai.NotesWriter.Wait.CHARGER -> tr("Notes on {0} of {1} chapters read. The rest wait for the charger.", done, of)
                dev.kolib.app.ai.NotesWriter.Wait.UNLOADED -> tr("Notes on {0} of {1} chapters read. The rest wait till the AI is asked something.", done, of)
                else -> tr("Notes on {0} of {1} chapters read. The rest are written while you read.", done, of)
            }
            else -> if (of == 0) tr("Notes are written on each chapter as you finish it.") else tr("Notes on {0} of {1} chapters read.", done, of)
        }
        Text(
            tr("It notes each chapter you finish, so it remembers the whole book so far. Writing them uses battery.") + " " + line,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp).testTag("chat-notes-status"),
        )
    }
}
