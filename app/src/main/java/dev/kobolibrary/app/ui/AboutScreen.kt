@file:OptIn(ExperimentalMaterial3Api::class)

package dev.kobolibrary.app.ui

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
        Notice("kepubify", "Books sent as kepubs are converted by kepubify's method.", "MIT License", "© 2017–2022 Patrick Gaskin", "kepubify-MIT.txt"),
        Notice("Lora", "Lettering on the covers the app makes.", OFL, "© 2011 The Lora Project Authors", "lora-OFL.txt"),
        Notice("Poppins", "Lettering on the covers the app makes.", OFL, "© 2020 The Poppins Project Authors", "poppins-OFL.txt"),
        Notice("Liberation Serif and Sans", "Covers the app makes, for alphabets Lora and Poppins don't have.", OFL, "© 2010 Google Corporation, © 2012 Red Hat, Inc.", "liberation-fonts.txt"),
        Notice("EB Garamond", "A typeface for reading.", OFL, "© 2017 The EB Garamond Project Authors", "eb-garamond-OFL.txt"),
        Notice("Literata", "A typeface for reading.", OFL, "© 2017 The Literata Project Authors", "literata-OFL.txt"),
        Notice("Atkinson Hyperlegible", "A typeface for reading.", OFL, "© 2020 Braille Institute of America, Inc.", "atkinson-hyperlegible-OFL.txt"),
        Notice("OpenDyslexic", "A typeface for reading.", OFL, "© 2012–2019 Abbie Gonzalez", "opendyslexic-OFL.txt"),
        Notice("Android Jetpack", "The app's screens, controls and icons (AndroidX, Jetpack Compose, Material icons).", APACHE, "© The Android Open Source Project", "apache-2.0.txt"),
        Notice("Kotlin", "The language the app is written in, with kotlinx.coroutines.", APACHE, "© JetBrains s.r.o. and Kotlin contributors", "apache-2.0.txt"),
    )
}

/** The app's name and version, and the licences of everything in it that comes from elsewhere. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf<Notice?>(null) }
    val showing = open
    BackHandler(enabled = showing != null) { open = null }
    if (showing != null) {
        LicenceText(showing, onBack = { open = null })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                    // The app's mark, in its own brass colours.
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(dev.kobolibrary.app.R.drawable.kolib_mark), null, Modifier.size(48.dp),
                    )
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                        Text("A library for your Kobo e-reader", style = MaterialTheme.typography.bodyMedium)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "The app has no access to the internet. Your books, and everything you do with them, stay on this device and your Kobo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Kobo is a trademark of Rakuten Kobo Inc. This app isn't made or endorsed by Rakuten Kobo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.widthIn(max = 640.dp).fillMaxWidth())
                Text(
                    "Open-source software and fonts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(Notices.all, key = { it.name }) { n ->
                ListItem(
                    headlineContent = { Text(n.name) },
                    supportingContent = { Text("${n.use}\n${n.licence} · ${n.holder}") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Read the licence") },
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
            .getOrElse { "The licence couldn't be read." }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(notice.name)
                        Text(notice.licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
