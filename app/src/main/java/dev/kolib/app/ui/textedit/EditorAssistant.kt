package dev.kolib.app.ui.textedit

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.AiEnd
import dev.kolib.app.ai.AiException
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.EditorAi
import dev.kolib.app.tr
import dev.kolib.app.ui.ai.AiSetupDialog
import dev.kolib.app.ui.components.Button
import dev.kolib.app.ui.components.grows
import dev.kolib.app.ui.components.StreamingText
import dev.kolib.app.ui.components.PartOf
import dev.kolib.app.ui.components.Part
import dev.kolib.app.ui.components.FloatingWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the assistant's changes go in without being shown first (the writer said so). */
object EditorAssistantChoice {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)
    fun auto(context: Context): Boolean = prefs(context).getBoolean("editor_auto", false)
    fun setAuto(context: Context, on: Boolean) = prefs(context).edit().putBoolean("editor_auto", on).apply()
}

/**
 * A change that's ready to go in: the new [text]; the paragraphs it's made of ([edits]) when it's whole
 * paragraphs being changed; and, in [note], why it's being shown first even to someone who said not to ask.
 */
private class Pending(val text: String, val edits: List<EditorAi.Edit>?, val note: String?)

/**
 * The AI assistant in the text editor, in a window floating over the page. It changes the text that's selected
 * the way it's told (fixes spelling, tidies formatting, rewrites), or carries on writing from the cursor. What
 * would replace the writer's own words is shown first and goes in only when they say so, unless they've let
 * it apply changes without asking; writing that only adds goes straight in. Either way it's one Undo to take back.
 *
 * Over several paragraphs it changes the words of plain paragraphs only, each staying the paragraph it was:
 * headings, pictures, links and styled text are never touched. Inside one paragraph, a change that would lose
 * a link, a picture or styling, and any change that comes back much shorter than what went in, is always
 * shown first.
 *
 * [given]: what's selected, for tests and pictures (the page is asked otherwise).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorAssistant(state: TextEditState, onDismiss: () -> Unit, given: TextEditState.Around? = null) {
    val context = LocalContext.current
    val hub = (context.applicationContext as KoLibApp).ai
    val ai by hub.settings.collectAsState()
    val files by hub.filesChanged.collectAsState()
    val ready = remember(ai, files) { hub.ready(ai) }
    val busy by hub.busy.collectAsState()
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf(false) }
    var around by remember { mutableStateOf(given) }
    var asked by remember { mutableStateOf(given != null) }
    var wish by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(EditorAssistantChoice.auto(context)) }
    val written = remember { MutableStateFlow("") }
    val shown by written.collectAsState()
    var working by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pending?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun read() = state.around {
        around = it
        asked = true
    }
    LaunchedEffect(Unit) { if (given == null) read() }

    fun apply(p: Pending) {
        if (p.edits != null) state.putRuns(p.edits.map { TextEditState.RunEdit(it.run, it.from, it.count, it.paragraphs.map(EditorAi::inner)) })
        else state.putBack(EditorAi.plain(p.text), EditorAi.html(p.text))
        result = null
        written.value = ""
        done = tr("Changed. Undo takes it back.")
        read()
    }

    fun add(text: String) {
        state.addAfter(EditorAi.plain(text), EditorAi.html(text))
        result = null
        written.value = ""
        done = tr("Added. Undo takes it back.")
        read()
    }

    fun run(instruction: String?) {
        val a = around ?: return
        val runs = a.runs
        val selected = if (runs != null) a.text else EditorAi.marked(a.html).ifBlank { a.text }
        if (instruction != null && selected.isBlank()) return
        job?.cancel()
        job = scope.launch {
            failed = null
            done = null
            result = null
            written.value = ""
            working = tr("Getting {0} ready…", hub.name())
            try {
                val (out, error) = hub.use { engine ->
                    val onPart = { part: Int, parts: Int -> working = if (parts > 1) tr("Working on part {0} of {1}…", part, parts) else tr("Working…") }
                    val onText = { piece: String -> written.update { it + piece } }
                    val r: Triple<AiEnd, String, List<EditorAi.Edit>?> = when {
                        instruction == null -> {
                            working = tr("Writing…")
                            val (end, text) = EditorAi.carryOn(engine, a.before + (if (a.text.isBlank()) "" else "\n\n" + a.text), wish, onText)
                            Triple(end, text, null)
                        }
                        runs != null -> {
                            val (end, edits) = EditorAi.changeRuns(engine, runs, instruction, onPart, onText)
                            Triple(end, edits.joinToString("\n\n") { it.paragraphs.joinToString("\n\n") }, edits)
                        }
                        else -> {
                            val (end, text) = EditorAi.change(engine, selected, instruction, onPart, onText)
                            Triple(end, text, null)
                        }
                    }
                    r to engine.lastError
                }
                val (end, text, edits) = out
                when {
                    end == AiEnd.FAILED || end == AiEnd.TOO_LONG -> failed = error ?: tr("The model couldn't do that.")
                    end == AiEnd.CANCELLED || text.isBlank() -> written.value = ""
                    // Adding takes nothing away, so it goes straight in.
                    instruction == null -> add(text)
                    else -> {
                        val was = if (edits != null && runs != null) edits.sumOf { e -> runs[e.run].subList(e.from, e.from + e.count).sumOf { it.length } } else EditorAi.plain(selected).length
                        val now = edits?.sumOf { e -> e.paragraphs.sumOf { it.length } } ?: EditorAi.plain(text).length
                        val note = when {
                            a.more -> tr("Links, pictures and special styling in it will be lost.")
                            now < was / 2 -> tr("This is much shorter than what you selected.")
                            else -> null
                        }
                        val pending = Pending(text, edits, note)
                        // A change waits to be looked at, unless told not to; one that would lose something always waits.
                        if (auto && note == null) apply(pending) else result = pending
                    }
                }
            } catch (e: AiException) {
                failed = e.message
            } finally {
                working = null
            }
        }
    }

    // A window as big as what's in it: its parts open out and close up, and it grows as the new text is written.
    FloatingWindow(onDismiss, maxWidth = 560.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp).testTag("editor-assistant")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(tr("AI assistant"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Close")) }
            }
            Column(Modifier.padding(end = 8.dp)) {
                if (!ready) {
                    Text(
                        tr("With an AI model set up, the assistant can fix spelling, tidy formatting, rewrite what you select, or carry on writing for you."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { setup = true }) { Text(tr("Set up AI")) }
                    return@Column
                }
                val a = around
                val words = a?.text?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
                val leftAlone = tr("Headings, and paragraphs with links, pictures or special styling, are left as they are.")
                Text(
                    when {
                        !asked -> tr("Looking at the page…")
                        a == null -> tr("Open the page (not its code) to use the assistant.")
                        words == 0 && a.runs != null -> leftAlone
                        words == 0 -> tr("Nothing is selected. Select the text to change, or let it carry on writing from where the cursor is.")
                        words == 1 -> tr("1 word selected.")
                        else -> tr("{0} words selected.", words)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (a != null && words > 0 && a.skipped > 0) Text(leftAlone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // What it can be told to do.
                Part(a != null && working == null && result == null) {
                    Column {
                        if (words == 0) Row {
                            TextButton(onClick = { state.select("paragraph"); read() }) { Text(tr("Select this paragraph")) }
                            TextButton(onClick = { state.select("all"); read() }) { Text(tr("Select the whole chapter")) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            AssistChip(onClick = { run(EditorAi.Quick.SPELLING.instruction) }, enabled = words > 0, label = { Text(tr("Fix spelling and punctuation")) }, modifier = Modifier.testTag("assistant-spelling"))
                            AssistChip(onClick = { run(EditorAi.Quick.TIDY.instruction) }, enabled = words > 0, label = { Text(tr("Tidy the formatting")) })
                            AssistChip(onClick = { run(EditorAi.Quick.CLEARER.instruction) }, enabled = words > 0, label = { Text(tr("Make it clearer")) })
                            AssistChip(onClick = { run(null) }, label = { Text(tr("Carry on writing")) }, modifier = Modifier.testTag("assistant-carry-on"))
                        }
                        OutlinedTextField(
                            value = wish, onValueChange = { wish = it }, label = { Text(tr("Or tell it what to do")) },
                            placeholder = { Text(tr("Make this more formal")) }, maxLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("assistant-wish"),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { run(wish) }, enabled = wish.isNotBlank() && words > 0) { Text(tr("Change the selection")) }
                        }
                    }
                }
                // What it's doing, and what it's written so far.
                PartOf(working) {
                    Column {
                        Text(if (busy == AiHub.State.OPENING) tr("Getting {0} ready…", hub.name()) else it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                }
                Part(shown.isNotBlank() && (working != null || result != null)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        StreamingText(
                            EditorAi.plain(result?.text ?: shown), working != null,
                            Modifier.grows().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp).testTag("assistant-preview"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Part(working != null) { TextButton(onClick = { job?.cancel() }) { Text(tr("Stop")) } }
                // A change to the writer's own words waits here to be accepted.
                PartOf(result) { pending ->
                    Column {
                        Text(tr("This will replace the text you selected."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        pending.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("assistant-note")) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button(onClick = { apply(pending) }, modifier = Modifier.testTag("assistant-apply")) { Text(tr("Replace")) }
                            TextButton(onClick = { result = null; written.value = "" }) { Text(tr("Discard")) }
                        }
                    }
                }
                PartOf(failed) { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                PartOf(done) { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("assistant-done")) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clickable { auto = !auto; EditorAssistantChoice.setAuto(context, auto) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Apply changes without asking"), style = MaterialTheme.typography.titleSmall)
                        Text(tr("Otherwise a change to your text is shown first, for you to accept."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it; EditorAssistantChoice.setAuto(context, it) }, modifier = Modifier.testTag("assistant-auto"))
                }
            }
        }
    }
    if (setup) AiSetupDialog(onDismiss = { setup = false })
}
