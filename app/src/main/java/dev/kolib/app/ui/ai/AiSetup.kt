package dev.kolib.app.ui.ai

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import dev.kolib.app.ui.components.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.AiDownload
import dev.kolib.app.ai.AiHub
import dev.kolib.app.ai.AiModel
import dev.kolib.app.ai.AiModels
import dev.kolib.app.ai.AiSettings
import dev.kolib.app.ai.ModelStore
import dev.kolib.app.ai.ServerEngine
import dev.kolib.app.tr
import dev.kolib.app.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Choosing what answers when a word is looked up, a chapter summarised or a question asked: a model on this
 * device (downloaded here, or a file of the user's own), a server of the user's, or nothing.
 */
@Composable
fun AiSetupDialog(onDismiss: () -> Unit, hub: AiHub = (LocalContext.current.applicationContext as KoLibApp).ai) {
    val context = LocalContext.current
    val settings by hub.settings.collectAsState()
    val downloads by hub.downloads.collectAsState()
    val changed by hub.filesChanged.collectAsState()
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    val own = remember(changed) { hub.models.own() }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayName(context, uri) ?: "model.gguf"
            adding = tr("Copying {0}…", name)
            problem = withContext(Dispatchers.IO) {
                try {
                    val file = context.contentResolver.openInputStream(uri)?.use { input ->
                        hub.models.addOwn(name, input) { bytes -> adding = tr("Copying {0}… {1}", name, Fmt.bytes(context, bytes).orEmpty()) }
                    }
                    if (file != null && !hub.ready()) hub.settingsStore.set(hub.settings.value.copy(source = "own:" + hub.models.ownName(file)))
                    null
                } catch (e: Exception) {
                    e.message ?: tr("That file couldn't be added.")
                }
            }
            adding = null
            hub.filesChanged.value++
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoAwesome, null) },
        title = { Text(tr("AI")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("ai-setup")) {
                Text(
                    tr("An AI model can explain a word as it's used where you're reading, summarise a chapter and answer questions about the book."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Section(tr("On this device"))
                if (hub.onDevice) {
                    Note(tr("Runs on this device, with no internet once it's downloaded. Nothing you read leaves it."))
                    AiModels.all.forEach { model ->
                        ModelRow(
                            model = model,
                            installed = remember(changed, downloads[model.id]) { hub.models.installed(model) },
                            selected = settings.modelId == model.id,
                            download = downloads[model.id],
                            suggested = hub.suggested.id == model.id,
                            fits = AiModels.fits(model, hub.memory),
                            onUse = { use(hub, settings.copy(source = "model:${model.id}")) },
                            onDownload = { hub.download(model) },
                            onStop = { hub.stopDownload(model) },
                            onDelete = { hub.delete(model) },
                        )
                    }
                    own.forEach { file ->
                        val name = hub.models.ownName(file)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { use(hub, settings.copy(source = "own:$name")) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = settings.ownName == name, onClick = { use(hub, settings.copy(source = "own:$name")) })
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(tr("Your own model · {0}", Fmt.bytes(context, file.length()).orEmpty()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { hub.deleteOwn(file) }) { Icon(Icons.Outlined.Delete, tr("Delete this model")) }
                        }
                    }
                    adding?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                    TextButton(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = adding == null, modifier = Modifier.testTag("ai-add-model")) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Add a model of your own"))
                    }
                    Note(tr("Any chat model in a GGUF file works: the kind llama.cpp runs, found on Hugging Face."))
                } else {
                    Note(tr("This device can't run AI models itself: that needs a 64-bit phone or tablet. You can still use a server of your own."))
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

                Section(tr("Your own server"))
                ServerFields(hub, settings)

                Section(tr("Off"))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { use(hub, settings.copy(source = "")) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.source.isEmpty(), onClick = { use(hub, settings.copy(source = "")) })
                    Text(tr("Don't use AI"), style = MaterialTheme.typography.titleSmall)
                }

                Section(tr("Dictionary"))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { hub.settingsStore.set(settings.copy(dictionary = !settings.dictionary)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(tr("Look words up in Wiktionary"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("The free dictionary, over the internet. Only the word is sent."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.dictionary, onCheckedChange = { hub.settingsStore.set(settings.copy(dictionary = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done")) } },
    )
}

private fun use(hub: AiHub, next: AiSettings) {
    hub.settingsStore.set(next)
    // Whatever was open is let go of; the one chosen is opened when it's first asked something.
    hub.release()
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

@Composable
private fun ModelRow(
    model: AiModel, installed: Boolean, selected: Boolean, download: AiDownload?, suggested: Boolean, fits: Boolean,
    onUse: () -> Unit, onDownload: () -> Unit, onStop: () -> Unit, onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = installed, onClick = onUse).padding(vertical = 4.dp).testTag("ai-model-${model.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected && installed, onClick = onUse, enabled = installed)
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.titleSmall)
            Text(
                tr("{0} · {1} · {2}", model.maker, Fmt.bytes(context, model.bytes).orEmpty(), model.licence),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(model.description, style = MaterialTheme.typography.bodySmall)
            if (suggested) Text(tr("Suggested for this device"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            else if (!fits) Text(tr("Wants more memory than this device has"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            if (download != null) {
                if (download.error != null) {
                    Text(download.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Spacer(Modifier.height(4.dp))
                    if (download.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { if (download.total > 0) download.bytes.toFloat() / download.total else 0f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (download.checking) tr("Checking the download…")
                        else tr("{0} of {1}", Fmt.bytes(context, download.bytes).orEmpty(), Fmt.bytes(context, download.total).orEmpty()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when {
            installed -> IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, tr("Delete this model")) }
            download != null && download.error == null -> TextButton(onClick = onStop) { Text(tr("Stop")) }
            download != null -> TextButton(onClick = onDownload) { Text(tr("Try again")) }
            else -> TextButton(onClick = onDownload) { Text(tr("Download")) }
        }
    }
}

@Composable
private fun ServerFields(hub: AiHub, settings: AiSettings) {
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var finding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Note(tr("For Ollama, LM Studio or llama.cpp on a computer of yours, or any service that speaks the OpenAI chat API. The words you look up and the pages around them are sent to it."))
    OutlinedTextField(
        value = settings.serverAddress, onValueChange = { hub.settingsStore.set(settings.copy(serverAddress = it)) },
        label = { Text(tr("Address")) }, placeholder = { Text("http://192.168.1.20:11434") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("ai-server-address"),
    )
    OutlinedTextField(
        value = settings.serverKey, onValueChange = { hub.settingsStore.set(settings.copy(serverKey = it)) },
        label = { Text(tr("Key (if it needs one)")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    val find = {
        finding = true
        problem = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ServerEngine.models(settings.serverAddress, settings.serverKey) } }
            finding = false
            found = result.getOrDefault(emptyList())
            if (found.isNotEmpty()) menu = true
            else problem = result.exceptionOrNull()?.message ?: tr("The server didn't list any models. Type the model's name.")
        }
        Unit
    }
    OutlinedTextField(
        value = settings.serverModel, onValueChange = { hub.settingsStore.set(settings.copy(serverModel = it)) },
        label = { Text(tr("Model")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("ai-server-model"),
    )
    androidx.compose.foundation.layout.Box {
        TextButton(enabled = settings.serverAddress.isNotBlank() && !finding, onClick = find) {
            Text(if (finding) tr("Looking…") else tr("See the server's models"))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            found.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    menu = false
                    hub.settingsStore.set(hub.settings.value.copy(serverModel = name))
                })
            }
        }
    }
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    val filled = settings.serverAddress.isNotBlank() && settings.serverModel.isNotBlank()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = filled) { use(hub, settings.copy(source = "server")) }.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(selected = settings.usesServer, onClick = { use(hub, settings.copy(source = "server")) }, enabled = filled)
        Text(tr("Use this server"), style = MaterialTheme.typography.titleSmall)
    }
}

/** What a file the user picked is called. */
internal fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
