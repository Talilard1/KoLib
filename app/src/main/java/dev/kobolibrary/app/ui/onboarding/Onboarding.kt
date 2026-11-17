package dev.kobolibrary.app.ui.onboarding

import dev.kobolibrary.app.UsbKobo
import dev.kobolibrary.app.tr
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kobolibrary.app.AppLanguage
import dev.kobolibrary.app.Connection
import dev.kobolibrary.app.R
import dev.kobolibrary.app.kobo.VolumeCandidate
import dev.kobolibrary.app.library.FolderScan
import dev.kobolibrary.app.ui.components.GoldButton
import dev.kobolibrary.app.ui.components.KoLibMark
import dev.kobolibrary.app.ui.theme.KoLibGold
import dev.kobolibrary.app.ui.theme.KoboLibraryTheme
import dev.kobolibrary.app.ui.theme.ThemeChoice
import kotlinx.coroutines.launch
import kotlin.math.max

/** The introduction's pages, in order. */
private enum class Intro { LANGUAGE, FOLDER, KOBO, COLLECTIONS, READING, DONE }

/** Where two of the introduction's pages are, for coming back to it from the reading setup. */
object IntroPages {
    /** The page that offers the reading setup. */
    val reading: Int get() = Intro.READING.ordinal
    /** The last page, after it. */
    val done: Int get() = Intro.DONE.ordinal
}

/**
 * What KoLib shows the first time it starts, after its opening: choosing a language, where your books
 * are, how to connect a Kobo, what to know about the Kobo's collections, and setting up the reader.
 * Like the opening, it's dark, whatever the app's own look.
 */
@Composable
fun Onboarding(
    connection: Connection,
    onChooseDrive: (VolumeCandidate?) -> Unit,
    onRetry: () -> Unit,
    scan: FolderScan?,
    onChooseFolder: () -> Unit,
    onAddFound: () -> Unit,
    onDone: () -> Unit,
    /** Opens the reading setup, which takes the screen; the introduction is shown again after it, from [startAt]. */
    onSetUpReading: () -> Unit = {},
    /** The page it starts at: after the reading setup, and for pictures of each page. */
    startAt: Int = 0,
) {
    // Light icons in the status bar, on the introduction's dark, whatever the app's look.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) bars.isAppearanceLightStatusBars = status
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    KoboLibraryTheme(ThemeChoice.DARK) {
        val pages = Intro.entries
        val pager = rememberPagerState(initialPage = startAt) { pages.size }
        val scope = rememberCoroutineScope()
        fun go(to: Int) {
            scope.launch { pager.animateScrollToPage(to.coerceIn(0, pages.size - 1)) }
        }
        val next = { if (pager.currentPage == pages.size - 1) onDone() else go(pager.currentPage + 1) }
        BackHandler(enabled = pager.currentPage > 0) { go(pager.currentPage - 1) }
        Box(
            Modifier.fillMaxSize().background(KoLibGold.night).drawBehind {
                val big = max(size.width, size.height)
                drawRect(
                    Brush.radialGradient(
                        0f to KoLibGold.glow.copy(alpha = 0.75f), 0.5f to KoLibGold.glow.copy(alpha = 0.22f), 1f to Color.Transparent,
                        center = Offset(size.width * 0.5f, size.height * 0.32f), radius = big * 0.8f,
                    )
                )
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth > maxHeight && maxWidth >= 700.dp
                val artHeight = if (wide) maxHeight * 0.62f else (maxHeight * 0.3f).coerceIn(150.dp, 300.dp)
                val page = pages[pager.currentPage]
                Column(Modifier.fillMaxSize()) {
                    // KoLib's mark and name; Skip on the pages that can be skipped.
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KoLibMark(Modifier.size(30.dp), glow = true)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("KoLib"), style = MaterialTheme.typography.titleLarge, color = KoLibGold.light)
                        Spacer(Modifier.weight(1f))
                        if (page == Intro.FOLDER || page == Intro.KOBO) {
                            TextButton(onClick = { go(pager.currentPage + 1) }) { Text(stringResource(R.string.intro_skip), color = KoLibGold.light) }
                        } else {
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                        when (pages[i]) {
                            // On a phone the globe is smaller, to leave room for the languages.
                            Intro.LANGUAGE -> IntroPage(wide, if (wide) artHeight else artHeight * 0.62f, { GlobeArt(it) }, stringResource(R.string.intro_language_title)) {
                                Body(stringResource(R.string.intro_language_text))
                                Spacer(Modifier.height(12.dp))
                                Languages()
                            }
                            Intro.FOLDER -> IntroPage(wide, artHeight, { FolderArt(it) }, stringResource(R.string.intro_folder_title)) {
                                Body(stringResource(R.string.intro_folder_text))
                                Spacer(Modifier.height(6.dp))
                                Body(stringResource(R.string.intro_folder_kinds), small = true)
                                Spacer(Modifier.height(16.dp))
                                FolderStatus(scan)
                            }
                            Intro.KOBO -> IntroPage(wide, artHeight, { KoboArt(it, connected = connection is Connection.Connected) }, stringResource(R.string.intro_kobo_title)) {
                                Step(1, stringResource(R.string.intro_kobo_step1))
                                Step(2, stringResource(R.string.intro_kobo_step2))
                                Step(3, stringResource(R.string.intro_kobo_step3))
                                Spacer(Modifier.height(14.dp))
                                KoboStatus(connection, onChooseDrive, onRetry)
                            }
                            Intro.COLLECTIONS -> IntroPage(wide, artHeight, { CollectionsArt(it) }, stringResource(R.string.intro_collections_title)) {
                                Body(stringResource(R.string.intro_collections_text))
                                Spacer(Modifier.height(14.dp))
                                Tip(stringResource(R.string.intro_collections_how))
                            }
                            Intro.READING -> IntroPage(wide, artHeight, { ReadingArt(it) }, stringResource(R.string.intro_reading_title)) {
                                Body(stringResource(R.string.intro_reading_text))
                            }
                            Intro.DONE -> IntroPage(wide, artHeight, { ShelfArt(it) }, stringResource(R.string.intro_done_title)) {
                                Body(stringResource(R.string.intro_done_text))
                            }
                        }
                    }
                    Dots(pages.size, pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
                    // The page's main button, and what else it offers.
                    Column(
                        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 440.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val button = Modifier.fillMaxWidth().height(52.dp)
                        when (page) {
                            Intro.FOLDER -> when (scan) {
                                is FolderScan.Looking -> GoldButton(onClick = {}, enabled = false, modifier = button) { Text(stringResource(R.string.intro_folder_looking_short)) }
                                is FolderScan.Found -> if (scan.books.isNotEmpty()) {
                                    GoldButton(onClick = { onAddFound(); next() }, modifier = button) {
                                        Text(pluralStringResource(R.plurals.intro_folder_add, scan.books.size, scan.books.size))
                                    }
                                    TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.intro_folder_another), color = KoLibGold.light) }
                                } else {
                                    GoldButton(onClick = onChooseFolder, modifier = button) { Text(stringResource(R.string.intro_folder_another)) }
                                }
                                is FolderScan.Adding -> GoldButton(onClick = next, modifier = button) { Text(stringResource(R.string.intro_continue)) }
                                else -> GoldButton(onClick = onChooseFolder, modifier = button) { Text(stringResource(R.string.intro_folder_choose)) }
                            }
                            Intro.READING -> {
                                GoldButton(onClick = onSetUpReading, modifier = button) { Text(stringResource(R.string.intro_reading_start)) }
                                TextButton(onClick = next) { Text(stringResource(R.string.intro_reading_later), color = KoLibGold.light) }
                            }
                            Intro.DONE -> GoldButton(onClick = onDone, modifier = button) { Text(stringResource(R.string.intro_start)) }
                            else -> GoldButton(onClick = next, modifier = button) { Text(stringResource(R.string.intro_continue)) }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun android.content.Context.activity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.activity()
    else -> null
}

/** A page: its picture, and beside it (on a wide screen) or under it, its title and what it says. */
@Composable
private fun IntroPage(wide: Boolean, artHeight: Dp, art: @Composable (Modifier) -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { art(Modifier.fillMaxWidth().height(artHeight)) }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(24.dp))
                Title(title, TextAlign.Start)
                Spacer(Modifier.height(14.dp))
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            art(Modifier.fillMaxWidth().height(artHeight))
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Title(title, TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                content()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * How lines break: between phrases in Japanese rather than anywhere (Android 13 and later), and in a
 * heading, into lines of about the same length.
 */
private val HeadingBreaks = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)
private val BodyBreaks = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

@Composable
private fun Title(text: String, align: TextAlign) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge.copy(lineBreak = HeadingBreaks),
        color = KoLibGold.spark,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Body(text: String, small: Boolean = false) {
    Text(
        text,
        style = (if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(lineBreak = BodyBreaks),
        color = KoLibGold.spark.copy(alpha = if (small) 0.62f else 0.84f),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A numbered step, as in the Kobo tab. */
@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = KoLibGold.gold, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("$number", color = KoLibGold.ink, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineBreak = BodyBreaks), color = KoLibGold.spark.copy(alpha = 0.88f), modifier = Modifier.padding(top = 3.dp))
    }
}

/** Something worth knowing, set apart in a card. */
@Composable
private fun Tip(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = KoLibGold.glow.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineBreak = BodyBreaks), color = KoLibGold.light, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

/** Which page this is, as dots: the current one long and gold. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.intro_page, current + 1, count)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 26.dp else 8.dp, label = "dot")
            Box(
                Modifier.width(w).height(8.dp).clip(CircleShape)
                    .background(if (i == current) KoLibGold.gold else KoLibGold.light.copy(alpha = 0.3f)),
            )
        }
    }
}

/** KoLib's languages, with the one it's in chosen: at first, the device's. */
@Composable
private fun Languages() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val device = remember { AppLanguage.deviceLanguage() }
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        for (tag in AppLanguage.supported) {
            val selected = tag == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selected) KoLibGold.glow.copy(alpha = 0.8f) else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        context.activity()?.let { AppLanguage.choose(it, tag) }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(AppLanguage.nativeName(tag), style = MaterialTheme.typography.titleMedium, color = KoLibGold.spark)
                    val local = AppLanguage.localName(tag, context)
                    val note = listOfNotNull(
                        local.takeIf { it != AppLanguage.nativeName(tag) },
                        stringResource(R.string.intro_device_language).takeIf { tag == device },
                    ).joinToString(" · ")
                    if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = KoLibGold.light.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** What's happening with the folder of books: being looked through, what's in it, being added. */
@Composable
private fun FolderStatus(scan: FolderScan?) {
    // Each stage fades into the next, and the one fading out keeps showing what it said.
    AnimatedContent(scan, contentKey = { it?.javaClass }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "folder") { scan ->
        when (scan) {
            null -> Unit
            is FolderScan.Looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KoLibGold.gold)
                Spacer(Modifier.width(12.dp))
                Body(
                    if (scan.found == 0) stringResource(R.string.intro_folder_looking, scan.folder)
                    else pluralStringResource(R.plurals.intro_folder_found_so_far, scan.found, scan.found, scan.folder),
                )
            }
            is FolderScan.Found -> Tip(
                if (scan.books.isEmpty()) stringResource(R.string.intro_folder_none, scan.folder)
                else pluralStringResource(R.plurals.intro_folder_found, scan.books.size, scan.books.size, scan.folder),
            )
            is FolderScan.Adding -> Tip(pluralStringResource(R.plurals.intro_folder_adding, scan.count, scan.count))
            is FolderScan.Failed -> Tip(stringResource(R.string.intro_folder_failed, scan.folder))
        }
    }
}

/** Where the Kobo is up to: waiting for it, asking for its drive, connected. */
@Composable
private fun KoboStatus(connection: Connection, onChooseDrive: (VolumeCandidate?) -> Unit, onRetry: () -> Unit) {
    when (connection) {
        is Connection.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KoLibGold.gold)
            Spacer(Modifier.width(12.dp))
            val opening = connection.usb == UsbKobo.OPENING || connection.usb == UsbKobo.SLOW
            Body(stringResource(if (opening) R.string.intro_kobo_detected else R.string.intro_kobo_waiting))
        }
        is Connection.NeedsAccess -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            OutlinedButton(onClick = { onChooseDrive(connection.candidate) }) { Text(stringResource(R.string.intro_kobo_allow), color = KoLibGold.light) }
            Spacer(Modifier.height(6.dp))
            Body(stringResource(R.string.intro_kobo_allow_help), small = true)
        }
        is Connection.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KoLibGold.gold)
            Spacer(Modifier.width(12.dp))
            Body(stringResource(R.string.intro_kobo_working))
        }
        is Connection.Connected -> Tip(stringResource(R.string.intro_kobo_connected, connection.session.info.displayName))
        is Connection.Failed -> Column(Modifier.fillMaxWidth()) {
            Tip(stringResource(R.string.intro_kobo_failed))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.intro_kobo_retry), color = KoLibGold.light) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Body(stringResource(R.string.intro_kobo_later), small = true)
}
