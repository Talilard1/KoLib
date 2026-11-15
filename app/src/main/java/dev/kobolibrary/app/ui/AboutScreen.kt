@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

import dev.kobolibrary.app.tr
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kobolibrary.app.BuildConfig
import dev.kobolibrary.app.R

/** Something in the app that comes from elsewhere: what it's used for, and the licence it's used under ([file], in assets/licences). */
data class Notice(val name: String, val use: String, val licence: String, val holder: String, val file: String)

object Notices {
    private const val OFL = "SIL Open Font License 1.1"
    private const val APACHE = "Apache License 2.0"

    val all = listOf(
        Notice("kepubify", tr("Books sent as kepubs are converted by kepubify's method."), tr("MIT License"), tr("© 2017–2022 Patrick Gaskin"), "kepubify-MIT.txt"),
        Notice(tr("Lora"), tr("Lettering on the covers the app makes."), OFL, tr("© 2011 The Lora Project Authors"), "lora-OFL.txt"),
        Notice(tr("Poppins"), tr("Lettering on the covers the app makes."), OFL, tr("© 2020 The Poppins Project Authors"), "poppins-OFL.txt"),
        Notice(tr("Liberation Serif and Sans"), tr("Covers the app makes, for alphabets Lora and Poppins don't have."), OFL, tr("© 2010 Google Corporation, © 2012 Red Hat, Inc."), "liberation-fonts.txt"),
        Notice(tr("EB Garamond"), tr("A typeface for reading, and for the app's headings and book titles."), OFL, tr("© 2017 The EB Garamond Project Authors"), "eb-garamond-OFL.txt"),
        Notice(tr("Literata"), tr("A typeface for reading."), OFL, tr("© 2017 The Literata Project Authors"), "literata-OFL.txt"),
        Notice(tr("Atkinson Hyperlegible"), tr("A typeface for reading."), OFL, tr("© 2020 Braille Institute of America, Inc."), "atkinson-hyperlegible-OFL.txt"),
        Notice(tr("OpenDyslexic"), tr("A typeface for reading."), OFL, tr("© 2012–2019 Abbie Gonzalez"), "opendyslexic-OFL.txt"),
        Notice(tr("Android Jetpack"), tr("The app's screens, controls and icons (AndroidX, Jetpack Compose, Material icons)."), APACHE, tr("© The Android Open Source Project"), "apache-2.0.txt"),
        Notice(tr("Kotlin"), tr("The language the app is written in, with kotlinx.coroutines."), APACHE, tr("© JetBrains s.r.o. and Kotlin contributors"), "apache-2.0.txt"),
        Notice("libarchive", tr("Reading CBR and CB7 comics."), tr("BSD 2-Clause License"), tr("© 2003–2018 Tim Kientzle and the libarchive contributors"), "libarchive-BSD.txt"),
        Notice(tr("libarchive for Android"), tr("Reading CBR and CB7 comics."), APACHE, tr("© 2023 Hai Zhang"), "apache-2.0.txt"),
    )

    /** KoLib itself: free software, to share and change under the GPL. */
    val own = Notice("KoLib", tr("This app. You can share it and change it under these terms."), tr("GNU General Public License 3.0"), tr("© 2026 the KoLib authors"), "gpl-3.0.txt")
}

/** The app's name and version, and the licences of everything in it that comes from elsewhere. */
@Composable
fun AboutScreen(onBack: () -> Unit, onShowIntro: (() -> Unit)? = null) {
    var open by remember { mutableStateOf<Notice?>(null) }
    val showing = open
    BackHandler(enabled = showing != null) { open = null }
    if (showing != null) {
        LicenceText(showing, onBack = { open = null })
        return
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(tr("About")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The app's mark and name, in gold, as at the end of the opening.
                    dev.kobolibrary.app.ui.components.KoLibMark(Modifier.size(64.dp), glow = true)
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(dev.kobolibrary.app.ui.theme.LocalKoLibStyle.current.markGold),
                            ),
                        )
                        Text(tr("A library for your Kobo e-reader"), style = MaterialTheme.typography.bodyMedium)
                        Text(tr("Version {0}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        tr("Your books, and everything you do with them, stay on this device and your Kobo. KoLib only goes online when you search for a cover, and then only sends the words you search for."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        tr("Kobo is a trademark of Rakuten Kobo Inc. This app isn't made or endorsed by Rakuten Kobo."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onShowIntro != null) {
                        androidx.compose.material3.OutlinedButton(onClick = onShowIntro) { Text(tr("Show the introduction again")) }
                    }
                }
                HorizontalDivider(Modifier.widthIn(max = 640.dp).fillMaxWidth())
                Text(
                    tr("Open-source software and fonts"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            item(key = "own") {
                val n = Notices.own
                ListItem(
                    headlineContent = { Text(n.name) },
                    supportingContent = { Text("${n.use}\n${n.licence} · ${n.holder}") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Read the licence")) },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().clickable { open = n }.padding(horizontal = 8.dp),
                )
            }
            items(Notices.all, key = { it.name }) { n ->
                ListItem(
                    headlineContent = { Text(n.name) },
                    supportingContent = { Text("${n.use}\n${n.licence} · ${n.holder}") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Read the licence")) },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().clickable { open = n }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** One licence, in full. */
@Composable
internal fun LicenceText(notice: Notice, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(notice.file) {
        runCatching { context.assets.open("licences/${notice.file}").bufferedReader().use { it.readText() } }
            .getOrElse { tr("The licence couldn't be read.") }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text(notice.name)
                        Text(notice.licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
            )
        },
    ) { padding ->
        SelectionContainer(Modifier.padding(padding).fillMaxSize()) {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            )
        }
    }
}
