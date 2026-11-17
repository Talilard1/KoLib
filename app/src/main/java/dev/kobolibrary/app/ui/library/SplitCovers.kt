package dev.kobolibrary.app.ui.library

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kobolibrary.app.tr
import dev.kobolibrary.app.ui.cover.CoverCrop
import dev.kobolibrary.app.ui.cover.CoverDesign
import dev.kobolibrary.app.ui.cover.CoverGenerator
import dev.kobolibrary.app.ui.cover.CoverSet
import dev.kobolibrary.app.ui.cover.CoverText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small covers drawn one at a time, away from the screen, and kept while the dialog is open. */
internal class CoverThumbs(context: Context, private val widthPx: Int) {
    // Made on first use, away from the screen: it reads its fonts.
    private val generator by lazy { CoverGenerator(context.applicationContext) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val one = Dispatchers.Default.limitedParallelism(1)
    private val made = HashMap<Pair<CoverText, CoverDesign>, ImageBitmap>()

    fun cached(text: CoverText, design: CoverDesign): ImageBitmap? = synchronized(made) { made[text to design] }

    suspend fun draw(text: CoverText, design: CoverDesign): ImageBitmap = cached(text, design) ?: withContext(one) {
        cached(text, design) ?: generator.render(text, design, widthPx, widthPx * CoverCrop.OUT_HEIGHT / CoverCrop.OUT_WIDTH)
            .asImageBitmap().also { synchronized(made) { made[text to design] = it } }
    }
}

@Composable
internal fun rememberCoverThumbs(width: Dp = 64.dp): CoverThumbs {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { width.roundToPx() }
    return remember(px) { CoverThumbs(context, px) }
}

/** A small cover in [design], drawn when it's first shown; the last one stays while a new one is drawn. */
@Composable
private fun CoverThumb(thumbs: CoverThumbs, text: CoverText, design: CoverDesign, modifier: Modifier = Modifier) {
    val picture by produceState(thumbs.cached(text, design), thumbs, text, design) {
        value = thumbs.draw(text, design)
    }
    Box(
        modifier
            .aspectRatio(CoverCrop.OUT_WIDTH.toFloat() / CoverCrop.OUT_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        picture?.let { Image(it, null, Modifier.fillMaxSize().testTag("cover-thumb"), contentScale = ContentScale.Crop) }
    }
}

/** One choice of design: a small cover and its name, ringed when it's the one chosen. */
@Composable
private fun DesignTile(label: String, selected: Boolean, onClick: () -> Unit, picture: @Composable (Modifier) -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
        picture(Modifier.fillMaxWidth().border(2.dp, ring, RoundedCornerShape(6.dp)).padding(3.dp))
        // A long word ("Fadenbindung") is set a little smaller and broken where a word may be, as in the cover maker.
        val longWord = label.split(' ', '-').maxOf { it.length } >= 10
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.let {
                if (longWord) it.copy(fontSize = 10.sp, letterSpacing = 0.sp, hyphens = Hyphens.Auto) else it.copy(hyphens = Hyphens.Auto)
            },
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * For a split into [books] books, all with covers in one design: [picked] (null: the app picks one that suits the
 * book), chosen from [suggestions], or from all the designs ([onBrowse]); with [eachColour], each in its own colour.
 * [textOf] is book k's lettering, for the covers shown.
 */
@Composable
internal fun SplitCoverChooser(
    thumbs: CoverThumbs,
    books: Int,
    textOf: (Int) -> CoverText,
    suggestions: List<CoverDesign>,
    picked: CoverDesign?,
    onPick: (CoverDesign?) -> Unit,
    onOtherSuggestions: () -> Unit,
    onBrowse: () -> Unit,
    eachColour: Boolean,
    onEachColour: (Boolean) -> Unit,
    /** Each book's colour dealt out afresh; [colourRound] says how many times. */
    colourRound: Int,
    onOtherColours: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val first = textOf(0)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "pick") {
                DesignTile(tr("Pick for me"), selected = picked == null, onClick = { onPick(null) }) { m ->
                    Box(
                        m.aspectRatio(CoverCrop.OUT_WIDTH.toFloat() / CoverCrop.OUT_HEIGHT)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Casino, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
            // One chosen from all the designs takes the place of the suggestion in its style, or else comes first.
            val shown = when {
                picked == null || picked in suggestions -> suggestions
                suggestions.any { it.style == picked.style } -> suggestions.map { if (it.style == picked.style) picked else it }
                else -> listOf(picked) + suggestions
            }
            items(shown, key = { it.hashCode() }) { d ->
                DesignTile(d.style.label, selected = d == picked, onClick = { onPick(d) }) { m -> CoverThumb(thumbs, first, d, m) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOtherSuggestions) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Other suggestions"))
            }
            TextButton(onClick = onBrowse) {
                Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("All designs…"))
            }
        }
        Text(
            if (picked == null) tr("A design that suits the book is picked when it's split.")
            else if (picked.style.themed && eachColour) tr("{0}, each book in its own colour.", picked.style.label)
            else if (picked.style.themed) tr("{0}, in {1}.", picked.style.label, picked.colors.name)
            else tr("{0}, in its picture's colours.", picked.style.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val colours = picked?.style?.themed != false
        // The words and the switch turn it on or off; the button beside them (not inside) deals the colours out again.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("A different colour for each book"),
                style = MaterialTheme.typography.bodyLarge,
                color = if (colours) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier
                    .weight(1f)
                    .toggleable(value = eachColour && colours, enabled = colours, role = Role.Switch, onValueChange = onEachColour)
                    .padding(vertical = 12.dp),
            )
            // Deals the colours out again, while there are covers to show them on.
            if (eachColour && colours && picked != null) {
                IconButton(onClick = onOtherColours) { Icon(Icons.Filled.Refresh, tr("Other colours")) }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = eachColour && colours, onCheckedChange = onEachColour, enabled = colours)
        }
        if (picked != null && books > 1) {
            Text(tr("How they'll look"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            val designs = remember(picked, eachColour, colourRound, books) {
                val set = CoverSet(picked, eachColour, colourRound)
                (0 until books).map { set.designFor(it, books) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(books) { k -> CoverThumb(thumbs, textOf(k), designs[k], Modifier.width(56.dp)) }
            }
        }
    }
}
