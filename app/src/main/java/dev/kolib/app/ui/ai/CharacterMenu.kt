package dev.kolib.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.ai.AiSettings
import dev.kolib.app.tr

/** Which instructions the character [c] has: its own, a preset's (by name), or some of the user's not kept as one. */
fun whatInstructions(s: AiSettings, c: Character): String {
    val text = s.instructionsOf(c.id)
    return when {
        text.isEmpty() -> tr("Their own instructions")
        else -> s.presets.firstOrNull { it.text == text }?.let { tr("“{0}” instructions", it.name) } ?: tr("Their own, changed")
    }
}

/** The faces of the characters [ids], overlapping a little: who's using something. */
@Composable
fun Heads(ids: List<String>, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    if (ids.isEmpty()) return
    Row(modifier) {
        ids.forEachIndexed { i, id ->
            MascotIcon(Characters.of(id), BotMood.IDLE, Modifier.offset(x = -(size / 3) * i).size(size))
        }
    }
}

/**
 * The name of the character the AI is, at the top of its window: tapped, the characters to switch to, and to rename
 * this one or make one's own.
 */
@Composable
fun CharacterTitle(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val settings by app.ai.settings.collectAsState()
    val character = chosenCharacter()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(end = 4.dp).testTag("ai-character"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(character.nameIn(settings), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, tr("Change the AI's character"))
        }
        CharacterMenu(open) { open = false }
    }
}

/**
 * The characters to switch to, each with the instructions it has (which it takes with it), the one the AI is
 * picked out; and to rename it, or make a character of one's own.
 */
@Composable
fun CharacterMenu(expanded: Boolean, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as KoLibApp
    val hub = app.ai
    val settings by hub.settings.collectAsState()
    val all = allCharacters()
    val character = chosenCharacter()
    var renaming by remember { mutableStateOf(false) }
    var making by remember { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        all.forEach { c ->
            val chosen = c.id == character.id
            DropdownMenuItem(
                text = {
                    Column {
                        Text(c.nameIn(settings), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            whatInstructions(settings, c), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingIcon = { MascotIcon(c, BotMood.IDLE, Modifier.size(32.dp)) },
                trailingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(18.dp).alpha(if (chosen) 1f else 0f)) },
                onClick = {
                    hub.settingsStore.set(settings.switchedTo(c.id))
                    onDismiss()
                },
                modifier = Modifier.testTag("ai-character-${c.id}"),
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(tr("Rename {0}", character.nameIn(settings))) }, onClick = { renaming = true },
            modifier = Modifier.testTag("ai-character-rename"),
        )
        DropdownMenuItem(text = { Text(tr("Make your own character")) }, onClick = { making = true }, modifier = Modifier.testTag("ai-character-make"))
    }
    if (renaming) NameDialog(
        title = tr("Rename {0}", character.nameIn(settings)), about = tr("Leave it empty for its own name again."),
        start = settings.mascotNames[character.id].orEmpty(), placeholder = character.defaultName,
        onDismiss = { renaming = false; onDismiss() },
    ) { name ->
        hub.settingsStore.set(settings.naming(character.id, name))
        renaming = false
        onDismiss()
    }
    if (making) CharacterMakerDialog(onDismiss = { making = false; onDismiss() }) { made ->
        hub.settingsStore.set(hub.settings.value.switchedTo(made.id))
        making = false
        onDismiss()
    }
}

/** Asks for a name: for a character, or anything else. Blank is allowed when [placeholder] says what it means. */
@Composable
fun NameDialog(title: String, about: String?, start: String, placeholder: String? = null, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text(tr("Name")) },
                    placeholder = placeholder?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("ai-name"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = placeholder != null || name.isNotBlank(), modifier = Modifier.testTag("ai-name-save")) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
