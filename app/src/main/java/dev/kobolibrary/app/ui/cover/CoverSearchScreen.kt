package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.tr
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.kobolibrary.app.covers.CoverSearch
import dev.kobolibrary.app.covers.CoverSource
import dev.kobolibrary.app.covers.FoundCover
import dev.kobolibrary.app.reader.ImageSize
import dev.kobolibrary.app.ui.components.GoldButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** A cover found online, with a small copy of it to show in the grid. */
private class Shown(val cover: FoundCover, val thumb: ImageBitmap?)

/**
 * Covers for a book found online: a grid of those on Amazon, Google Books and Open Library, each with
 * its size in pixels; or Google Images itself, where pressing and holding a picture picks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(
    title: String,
    authors: List<String>,
    isbn: String?,
    onPick: (bytes: ByteArray, label: String) -> Unit,
    onClose: () -> Unit,
    /** For tests: covers already found, instead of searching. */
    initial: List<FoundCover>? = null,
) {
    BackHandler(onBack = onClose)
    val firstQuery = remember(title, authors) { listOf(title, authors.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" ") }
    var typed by remember { mutableStateOf(firstQuery) }
    // What's searched for: the book's title and author to start with, then whatever is typed.
    var searched by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    val found = remember { mutableStateListOf<Shown>() }
    var searching by remember { mutableStateOf(initial == null) }
    var picked by remember { mutableStateOf<Shown?>(null) }

    LaunchedEffect(round) {
        if (initial != null) {
            found.clear()
            initial.forEach { found += Shown(it, withContext(Dispatchers.Default) { thumbnail(it.bytes) }) }
            return@LaunchedEffect
        }
        found.clear()
        searching = true
        runCatching {
            CoverSearch().search(title, authors, isbn, searched) { c ->
                val shown = Shown(c, thumbnail(c.bytes))
                withContext(Dispatchers.Main) {
                    // Amazon's first, then Google Books, then Open Library; the biggest first within each.
                    val at = found.indexOfFirst { o -> o.cover.source.ordinal > c.source.ordinal || (o.cover.source == c.source && o.cover.width * o.cover.height < c.width * c.height) }
                    if (at < 0) found += shown else found.add(at, shown)
                }
            }
        }
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
                title = { Text(tr("Find a cover")) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val go: () -> Unit = {
                    searched = typed.trim().takeIf { it.isNotEmpty() && it != firstQuery }
                    round++
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(tr("Search for")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = go) { Icon(Icons.Outlined.Search, tr("Search")) }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("Amazon and book sites")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("Google Images")) })
            }
            if (tab == 0) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(132.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(found, key = { it.cover.url }) { s -> FoundTile(s) { picked = s } }
                    }
                    when {
                        searching && found.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(tr("Looking on Amazon, Google Books and Open Library…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        !searching && found.isEmpty() -> Text(
                            tr("No covers found. Check you're online, try other words, or look on Google Images."),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                        searching -> CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(16.dp).size(28.dp), strokeWidth = 3.dp)
                    }
                }
            } else {
                GoogleImages(searched ?: tr("{0} book cover", firstQuery), Modifier.weight(1f).fillMaxWidth()) { cover -> picked = cover }
            }
        }
    }

    picked?.let { s ->
        PickedCover(
            s,
            onUse = {
                picked = null
                onPick(s.cover.bytes, "${s.cover.source.label}, ${s.cover.width} × ${s.cover.height}")
            },
            onCancel = { picked = null },
        )
    }
}

/**
 * A picture for a cover found on Google Images: Google's own page, searching for [firstQuery] or whatever
 * is typed, where pressing and holding a picture picks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureSearchScreen(firstQuery: String, onPick: (bytes: ByteArray) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var typed by remember { mutableStateOf(firstQuery) }
    var searched by remember { mutableStateOf(firstQuery) }
    var picked by remember { mutableStateOf<Shown?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
                title = { Text(tr("Find a picture")) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val go: () -> Unit = { searched = typed.trim().ifEmpty { firstQuery } }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(tr("Search for")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = go) { Icon(Icons.Outlined.Search, tr("Search")) }
            }
            GoogleImages(searched, Modifier.weight(1f).fillMaxWidth()) { picture -> picked = picture }
        }
    }
    picked?.let { s ->
        PickedCover(
            s,
            useLabel = tr("Use this picture"),
            onUse = {
                picked = null
                onPick(s.cover.bytes)
            },
            onCancel = { picked = null },
        )
    }
}

/** A cover in the grid: the picture, where it's from, and its size at the bottom right. */
@Composable
private fun FoundTile(s: Shown, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            s.thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            SizeBadge(s.cover.width, s.cover.height, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Text(
            s.cover.source.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A picture's size in pixels, on a dark label so it reads over any cover. */
@Composable
private fun SizeBadge(w: Int, h: Int, modifier: Modifier = Modifier) {
    Text(
        "$w × $h",
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** The cover picked, larger, to use or not. */
@Composable
private fun PickedCover(s: Shown, onUse: () -> Unit, onCancel: () -> Unit, useLabel: String = tr("Use this cover")) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.heightIn(max = 420.dp).aspectRatio(s.cover.width.toFloat() / s.cover.height)) {
                    s.thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                    SizeBadge(s.cover.width, s.cover.height, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("${s.cover.source.label} · ${s.cover.width} × ${s.cover.height}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text(tr("Cancel")) }
                    GoldButton(onClick = onUse) { Text(useLabel) }
                }
            }
        }
    }
}

/**
 * Google Images itself, searching for [query]: Google doesn't let apps take its results, so the page is
 * shown as it is. Pressing and holding a picture picks it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleImages(query: String, modifier: Modifier, onPicture: (Shown) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Text(
            problem ?: if (loading) tr("Getting the picture…") else tr("Tap a picture to enlarge it, then hold it to use it."),
            style = MaterialTheme.typography.bodySmall,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val url = "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(query, "UTF-8")
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    val take = { src: String? ->
                        if (src != null) {
                            loading = true
                            problem = null
                            scope.launch {
                                val shown = withContext(Dispatchers.IO) {
                                    CoverSearch.picture(src)?.let { bytes ->
                                        ImageSize.of(bytes)?.let { (w, h) ->
                                            Shown(FoundCover(CoverSource.GOOGLE_IMAGES, src, bytes, w, h), thumbnail(bytes))
                                        }
                                    }
                                }
                                loading = false
                                if (shown == null) problem = tr("Couldn't load that picture. Tap it to enlarge it, then try again.")
                                else onPicture(shown)
                            }
                        }
                    }
                    setOnLongClickListener { v ->
                        val web = v as WebView
                        val hit = web.hitTestResult
                        when (hit.type) {
                            WebView.HitTestResult.IMAGE_TYPE -> {
                                take(hit.extra)
                                true
                            }
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                // A picture that's also a link: ask for the picture's own address.
                                val msg = Message.obtain(Handler(Looper.getMainLooper()) { m ->
                                    take(m.data.getString("src"))
                                    true
                                })
                                web.requestImageRef(msg)
                                true
                            }
                            else -> false
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { web -> if (web.url?.contains(URLEncoder.encode(query, "UTF-8")) != true && web.url != null) web.loadUrl(url) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/** A small copy of a picture for the grid. */
private fun thumbnail(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 360 && bounds.outHeight / (sample * 2) >= 360) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
