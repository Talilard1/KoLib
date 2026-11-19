package dev.kolib.app.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Making a character of one's own: the character sheet to fill in (drawn on, or given to an AI that makes pictures,
 * with what to tell it), then the finished sheet chosen, its pictures shown, and a name and a way of talking given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterMakerDialog(onDismiss: () -> Unit, onMade: (OwnCharacter) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var frames by remember { mutableStateOf<Map<SheetFrame, Bitmap>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        problem = null
        scope.launch {
            val cut = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("couldn't read it")
                    val sheet = CharacterSheet.decode(bytes) ?: error("not a picture")
                    CharacterSheet.cut(sheet)
                }.onFailure { AppLog.w("AI", "Couldn't read a character sheet", it) }.getOrNull()
            }
            reading = false
            if (cut == null) problem = tr("No character was found on that picture. Draw them in the green squares, square 1 at least, and keep the background plain.")
            else frames = cut
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Make your own character")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("ai-maker")) {
                Text(
                    tr("Draw your character in each square of the character sheet, or give the sheet to an AI that makes pictures, with the instructions to go with it. Then choose the finished sheet here."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedButton(onClick = { scope.launch { saveSheet(context) } }, modifier = Modifier.testTag("ai-sheet-save")) { Text(tr("Save the sheet")) }
                    OutlinedButton(onClick = { scope.launch { shareSheet(context) } }) { Text(tr("Share the sheet")) }
                    OutlinedButton(onClick = { copyInstructions(context) }, modifier = Modifier.testTag("ai-sheet-copy")) { Text(tr("Copy the instructions")) }
                }
                Button(
                    onClick = { runCatching { choose.launch("image/*") }.onFailure { problem = tr("This device has no picture picker.") } },
                    enabled = !reading, modifier = Modifier.padding(top = 14.dp).testTag("ai-sheet-choose"),
                ) { Text(if (frames == null) tr("Choose the finished sheet") else tr("Choose another sheet")) }
                if (reading) CircularProgressIndicator(Modifier.padding(top = 10.dp).size(28.dp))
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                frames?.let { f ->
                    // The pictures found, as the character will be.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 10.dp).testTag("ai-sheet-frames")) {
                        f.values.forEach { b ->
                            Image(
                                b.asImageBitmap(), null,
                                Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text(tr("Name")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("ai-maker-name"),
                )
                OutlinedTextField(
                    value = personality, onValueChange = { personality = it.take(600) }, label = { Text(tr("How they are and how they talk")) },
                    placeholder = { Text(tr("For example: a grumpy old pirate with a soft heart, who loves poetry")) },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("ai-maker-personality"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val f = frames ?: return@TextButton
                    scope.launch {
                        val made = withContext(Dispatchers.IO) {
                            runCatching { Characters.make(context, name, personality, f) }
                                .onFailure { AppLog.w("AI", "Couldn't make a character", it) }.getOrNull()
                        }
                        if (made == null) problem = tr("The character couldn't be saved.") else onMade(made)
                    }
                },
                enabled = frames != null && name.isNotBlank(), modifier = Modifier.testTag("ai-maker-make"),
            ) { Text(tr("Make")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

private const val SHEET_NAME = "KoLib character sheet.png"

/** Saves the empty sheet to Pictures/KoLib, and says how that went. */
private suspend fun saveSheet(context: Context) {
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, SHEET_NAME)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KoLib")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no place for it")
            context.contentResolver.openOutputStream(uri)?.use { out -> context.assets.open(CharacterSheet.ASSET).use { it.copyTo(out) } } ?: error("not written")
        }.onFailure { AppLog.w("AI", "Couldn't save the character sheet", it) }.isSuccess
    }
    Toast.makeText(context, if (ok) tr("Saved to Pictures/KoLib.") else tr("The sheet couldn't be saved."), Toast.LENGTH_SHORT).show()
}

/** Offers the empty sheet, with the instructions for an AI, to another app. */
private suspend fun shareSheet(context: Context) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, SHEET_NAME).also { f -> context.assets.open(CharacterSheet.ASSET).use { input -> f.outputStream().use { input.copyTo(it) } } }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, CharacterSheet.INSTRUCTIONS)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, tr("Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copyInstructions(context: Context) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("KoLib", CharacterSheet.INSTRUCTIONS))
    }
    Toast.makeText(context, tr("Copied the instructions."), Toast.LENGTH_SHORT).show()
}
