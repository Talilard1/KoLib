package dev.kolib.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kolib.app.ai.AiDownload
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.Fit
import dev.kolib.app.ai.FoundFile
import dev.kolib.app.ai.FoundModel
import dev.kolib.app.ai.ModelSearch
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Fmt
import dev.kolib.app.ui.components.FloatingWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What's known of a found model's files: being looked for, found (perhaps none), or not reachable. */
private sealed interface Files {
    data object Looking : Files
    data object Unreachable : Files
    data class Found(val files: List<FoundFile>) : Files
}

/**
 * Finding a model on Hugging Face to download: a search, with what's found listed by how it suits the
 * device (only what runs comfortably here, or everything), each with the size of file to take and its
 * other sizes. Anything there can be downloaded, whoever made it and however it answers.
 */
@Composable
fun ModelSearchWindow(hub: AiHub, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var suits by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<FoundModel>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val files = remember { mutableStateMapOf<String, Files>() }
    val gate = remember { Semaphore(4) }
    val downloads by hub.downloads.collectAsState()
    val changed by hub.filesChanged.collectAsState()
    val settings by hub.settings.collectAsState()

    LaunchedEffect(query, suits) {
        results = null
        failed = false
        try {
            results = withContext(Dispatchers.IO) { ModelSearch.search(query, if (suits) hub.memory else null) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
        }
    }

    FloatingWindow(onDismiss, Modifier.testTag("model-search"), maxWidth = 600.dp) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Find a model"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Close")) }
        }
        OutlinedTextField(
            value = typed, onValueChange = { typed = it }, singleLine = true,
            placeholder = { Text(tr("Search Hugging Face")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = typed.trim() }),
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("model-search-box"),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = suits, onClick = { suits = true }, label = { Text(tr("Suits this device")) }, modifier = Modifier.testTag("model-search-suits"))
            FilterChip(selected = !suits, onClick = { suits = false }, label = { Text(tr("All sizes")) }, modifier = Modifier.testTag("model-search-all"))
            // Things people look for: the big families, and models that answer without holding back.
            listOf("Gemma", "Qwen", "Llama", "Mistral", "Phi", tr("Uncensored") to "uncensored").forEach { s ->
                val (label, words) = if (s is Pair<*, *>) s.first as String to s.second as String else s as String to s
                AssistChip(onClick = { typed = words; query = words }, label = { Text(label) })
            }
        }
        Text(
            tr("From Hugging Face. These are other people's models, and KoLib hasn't checked them."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val found = results
        when {
            failed -> Text(tr("Couldn't reach Hugging Face. Check the internet connection."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            found == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp))
            found.isEmpty() -> Text(tr("Nothing found."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            else -> {
                // Asked for what suits the device, what turns out too big for it once its files are known is left out.
                val shown = found.filter { m ->
                    val known = (files[m.repo] as? Files.Found)?.files
                    if (suits && known != null) known.any { ModelSearch.fit(ModelSearch.held(it), hub.memory) != Fit.TOO_BIG } else true
                }
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().padding(bottom = 10.dp)) {
                    items(shown, key = { it.repo }) { model ->
                        LaunchedEffect(model.repo) {
                            if (model.gated || files[model.repo] is Files.Found) return@LaunchedEffect
                            files[model.repo] = Files.Looking
                            files[model.repo] = try {
                                gate.withPermit { withContext(Dispatchers.IO) { Files.Found(ModelSearch.files(model.repo)) } }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Files.Unreachable
                            }
                        }
                        FoundModelRow(hub, model, files[model.repo], downloads, changed, settings.ownName)
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fk".format(n / 1_000.0)
    else -> n.toString()
}

/** A model that was found: who made it and how often it's been taken, the file of it to take here, and its other sizes. */
@Composable
private fun FoundModelRow(hub: AiHub, model: FoundModel, files: Files?, downloads: Map<String, AiDownload>, changed: Int, using: String?) {
    var all by remember(model.repo) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("found-${model.repo}")) {
        Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(model.maker, tr("{0} downloads", compact(model.downloads)), model.licence).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            model.gated -> Text(tr("This one has to be asked for on Hugging Face first, so it can't be downloaded here."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            files == null || files is Files.Looking -> Text(tr("Looking…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            files is Files.Unreachable -> Text(tr("Couldn't reach Hugging Face. Check the internet connection."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            files is Files.Found && files.files.isEmpty() -> Text(tr("No file here that KoLib can run."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            files is Files.Found -> {
                val best = remember(files) { ModelSearch.best(files.files, hub.memory) }
                best?.let { FoundFileRow(hub, it, model.licence, downloads, changed, using) }
                if (files.files.size > 1) {
                    Text(
                        if (all) tr("Fewer sizes") else tr("Other sizes"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(vertical = 6.dp),
                    )
                    if (all) files.files.filter { it != best }.forEach { FoundFileRow(hub, it, model.licence, downloads, changed, using) }
                }
            }
        }
    }
}

/** One size of a found model: how big it is and how that suits the device, and its download. */
@Composable
private fun FoundFileRow(hub: AiHub, file: FoundFile, licence: String?, downloads: Map<String, AiDownload>, changed: Int, using: String?) {
    val context = LocalContext.current
    val model = remember(file) { ModelSearch.asModel(file, licence) }
    val here = remember(file, changed) { hub.models.installed(model) }
    val download = downloads[model.id]
    val fit = ModelSearch.fit(ModelSearch.held(file), hub.memory)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(file.quant.takeIf { it.isNotEmpty() }, Fmt.bytes(context, file.bytes)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when (fit) {
                    Fit.GOOD -> tr("Suits this device")
                    Fit.TIGHT -> tr("A tight fit: it may be slow, or close other apps")
                    Fit.TOO_BIG -> tr("Wants more memory than this device has")
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (fit) { Fit.GOOD -> MaterialTheme.colorScheme.primary; Fit.TIGHT -> MaterialTheme.colorScheme.onSurfaceVariant; Fit.TOO_BIG -> MaterialTheme.colorScheme.error },
            )
            DownloadProgress(download)
        }
        Spacer(Modifier.height(0.dp))
        val name = hub.models.ownName(hub.models.file(model))
        when {
            here && using == name -> Text(tr("In use"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
            here -> TextButton(onClick = {
                hub.settingsStore.set(hub.settings.value.copy(source = hub.sourceOf(model)))
                hub.release()
            }) { Text(tr("Use")) }
            download != null && download.error == null -> TextButton(onClick = { hub.stopDownload(model) }) { Text(tr("Stop")) }
            download != null -> TextButton(onClick = { hub.download(model) }) { Text(tr("Try again")) }
            else -> TextButton(onClick = { hub.download(model) }, modifier = Modifier.testTag("found-download-${file.fileName}")) { Text(tr("Download")) }
        }
    }
}
