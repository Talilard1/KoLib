@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.ui.components.GoldButton
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Makes a cover for a book: pick one of the designs and a colour theme (a built-in one or any
 * colour, dark or light), or let it pick both by chance.
 */
@Composable
fun CoverGeneratorScreen(
    text: CoverText,
    onUse: (Bitmap, CoverDesign) -> Unit,
    onCancel: () -> Unit,
    initial: CoverDesign = remember { CoverDesign.random(anyColour = false) },
) {
    val context = LocalContext.current
    val generator = remember { CoverGenerator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(initial.style) }
    var themeIndex by remember { mutableStateOf(CoverColors.themes.indexOfFirst { it.name == initial.colors.name }.takeIf { it >= 0 }) }
    var hue by remember { mutableStateOf(210f) }
    var strength by remember { mutableStateOf(0.7f) }
    var dark by remember { mutableStateOf(true) }
    val colors = themeIndex?.let { CoverColors.themes[it] } ?: CoverColors.fromHue(hue, strength, dark)
    val design = CoverDesign(style, colors)
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var thumbs by remember { mutableStateOf<Map<CoverStyle, ImageBitmap>>(emptyMap()) }
    var making by remember { mutableStateOf(false) }

    BackHandler { onCancel() }

    LaunchedEffect(design) {
        preview = withContext(Dispatchers.Default) { generator.render(text, design, 536, 724).asImageBitmap() }
    }
    LaunchedEffect(colors) {
        thumbs = withContext(Dispatchers.Default) {
            CoverStyle.entries.associateWith { generator.render(text, CoverDesign(it, colors), 134, 181).asImageBitmap() }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
                title = { Text("Make a cover") },
                actions = {
                    if (making) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    GoldButton(
                        onClick = {
                            making = true
                            scope.launch {
                                val full = withContext(Dispatchers.Default) { generator.render(text, design) }
                                making = false
                                onUse(full, design)
                            }
                        },
                        enabled = !making,
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text("Use this cover") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val previewPane: @Composable (Modifier) -> Unit = { m ->
                BoxWithConstraints(m.padding(24.dp), contentAlignment = Alignment.Center) {
                    val w = min(maxWidth, maxHeight * CoverCrop.ASPECT)
                    Box(
                        Modifier
                            .width(w)
                            .aspectRatio(CoverCrop.ASPECT)
                            .shadow(8.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        preview?.let { Image(it, contentDescription = "The cover", modifier = Modifier.fillMaxSize()) } ?: CircularProgressIndicator()
                    }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = {
                        val d = CoverDesign.random()
                        style = d.style
                        val idx = CoverColors.themes.indexOfFirst { it == d.colors }
                        if (idx >= 0) {
                            themeIndex = idx
                        } else {
                            themeIndex = null
                            hue = kotlin.random.Random.nextFloat() * 360f
                            strength = 0.45f + kotlin.random.Random.nextFloat() * 0.55f
                            dark = kotlin.random.Random.nextBoolean()
                        }
                    }) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Surprise me")
                    }
                    Text("Style", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CoverStyle.entries.forEach { s ->
                            val on = s == style
                            Column(
                                Modifier
                                    .width(78.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { style = s }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(CoverCrop.ASPECT)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            RoundedCornerShape(4.dp),
                                        ),
                                ) {
                                    thumbs[s]?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                                }
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Colour", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverColors.themes.forEachIndexed { i, theme ->
                            Swatch(theme, selected = themeIndex == i, label = theme.name) { themeIndex = i }
                        }
                        Swatch(CoverColors.fromHue(hue, strength, dark), selected = themeIndex == null, label = "Your colour") { themeIndex = null }
                    }
                    if (themeIndex == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dark, onClick = { dark = true }, label = { Text("Dark cover") })
                            FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("Light cover") })
                        }
                        Text("Colour", style = MaterialTheme.typography.labelLarge)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f % 360f, 0.55f, 0.85f) }))
                        )
                        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                        Text("Strength", style = MaterialTheme.typography.labelLarge)
                        Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.1f..1f)
                    }
                    Text(
                        "The cover shows the book's title, series and authors. It's made at 1072 × 1448 pixels, the size of the Kobo Clara's screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    previewPane(Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(400.dp).fillMaxHeight()) {
                        controls(Modifier.fillMaxSize())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    previewPane(Modifier.weight(1f).fillMaxWidth())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        controls(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(colors: CoverColors, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                // The board's colour, with a wedge of the accent at the foot.
                .background(Brush.verticalGradient(0f to Color(colors.ground), 0.64f to Color(colors.ground), 0.64f to Color(colors.accent), 1f to Color(colors.accent)))
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color(colors.ink), modifier = Modifier.size(18.dp).offset(y = (-5).dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
