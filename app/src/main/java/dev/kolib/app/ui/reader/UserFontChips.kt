package dev.kolib.app.ui.reader

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.kolib.app.KoLibApp
import dev.kolib.app.reader.ReaderSettings
import dev.kolib.app.tr
import dev.kolib.app.ui.ai.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader's own fonts, after the built-in ones: a chip for each family the user has added (its name set in
 * itself), and one to add more from font files. The family in use can be removed from its chip.
 */
@Composable
internal fun UserFontChips(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    val context = LocalContext.current
    val fonts = (context.applicationContext as KoLibApp).fonts
    val version by fonts.version.collectAsState()
    val families = remember(version) { fonts.families() }
    val scope = rememberCoroutineScope()
    val now by rememberUpdatedState(s)
    val change by rememberUpdatedState(onChange)
    var problem by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var added: String? = null
            val failed = withContext(Dispatchers.IO) {
                var why: String? = null
                for (uri in uris) {
                    try {
                        val name = displayName(context, uri) ?: "font"
                        context.contentResolver.openInputStream(uri)?.use { added = fonts.add(name, it) }
                    } catch (e: Exception) {
                        why = e.message ?: tr("That font couldn't be added.")
                    }
                }
                why
            }
            problem = failed
            added?.let { change(now.copy(userFont = it)) }
        }
    }

    families.forEach { family ->
        val face = remember(family) {
            fonts.fileOf(family.name)?.let { f -> runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull() }
        }
        val selected = s.userFont == family.name
        FilterChip(
            selected = selected,
            onClick = { onChange(s.copy(userFont = family.name)) },
            label = { Text(family.name, fontFamily = face) },
            trailingIcon = if (selected) {
                { Icon(Icons.Filled.Close, tr("Remove this font"), Modifier.size(18.dp).clickable { removing = family.name }) }
            } else null,
            modifier = Modifier.testTag("font-${family.name}"),
        )
    }
    AssistChip(
        onClick = { pick.launch(arrayOf("*/*")) },
        label = { Text(tr("Add a font")) },
        leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
        modifier = Modifier.testTag("font-add"),
    )

    problem?.let { why ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(tr("Couldn't add the font")) },
            text = { Text(why) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text(tr("OK")) } },
        )
    }
    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tr("Remove “{0}”?", name)) },
            text = { Text(tr("Books set in it go back to {0}.", s.font.label)) },
            confirmButton = {
                TextButton(onClick = {
                    fonts.remove(name)
                    removing = null
                    if (now.userFont == name) change(now.copy(userFont = ""))
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tr("Cancel")) } },
        )
    }
}
