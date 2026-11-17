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
        Notice("kepubify", tr("Kepub conversion follows kepubify's method."), tr("MIT License"), tr("© 2017–2022 Patrick Gaskin"), "kepubify-MIT.txt"),
        Notice(tr("Lora"), tr("Generated covers."), OFL, tr("© 2011 The Lora Project Authors"), "lora-OFL.txt"),
        Notice(tr("Poppins"), tr("Generated covers."), OFL, tr("© 2020 The Poppins Project Authors"), "poppins-OFL.txt"),
        Notice(tr("Cinzel"), tr("Generated covers."), OFL, tr("© 2020 The Cinzel Project Authors"), "cinzel-OFL.txt"),
        Notice(tr("Cinzel Decorative"), tr("Generated covers."), OFL, tr("© 2012 Natanael Gama"), "cinzel-decorative-OFL.txt"),
        Notice(tr("UnifrakturMaguntia"), tr("Generated covers."), OFL, tr("© 2010 j. 'mach' wust, © 2009 Peter Wiegel"), "unifraktur-maguntia-OFL.txt"),
        Notice(tr("Great Vibes"), tr("Generated covers."), OFL, tr("© 2015 The Great Vibes Pro Project Authors"), "great-vibes-OFL.txt"),
        Notice(tr("Bebas Neue"), tr("Generated covers."), OFL, tr("© 2010 Dharma Type"), "bebas-neue-OFL.txt"),
        Notice(tr("Orbitron"), tr("Generated covers."), OFL, tr("© 2018 The Orbitron Project Authors"), "orbitron-OFL.txt"),
        Notice(tr("Poiret One"), tr("Generated covers."), OFL, tr("© 2011 The Poiret One Project Authors"), "poiret-one-OFL.txt"),
        Notice(tr("Fredoka"), tr("Generated covers."), OFL, tr("© 2016 The Fredoka Project Authors"), "fredoka-OFL.txt"),
        Notice(tr("Playfair Display"), tr("Generated covers."), OFL, tr("© 2017 The Playfair Display Project Authors"), "playfair-display-OFL.txt"),
        Notice(tr("IM Fell English"), tr("Generated covers."), OFL, tr("© 2010 Igino Marini"), "im-fell-english-OFL.txt"),
        Notice(tr("Courier Prime"), tr("Generated covers."), OFL, tr("© 2015 The Courier Prime Project Authors"), "courier-prime-OFL.txt"),
        Notice(tr("Space Mono"), tr("Generated covers."), OFL, tr("© 2016 The Space Mono Project Authors"), "space-mono-OFL.txt"),
        Notice(tr("Caveat"), tr("Generated covers."), OFL, tr("© 2014 The Caveat Project Authors"), "caveat-OFL.txt"),
        Notice(tr("Pacifico"), tr("Generated covers."), OFL, tr("© 2018 The Pacifico Project Authors"), "pacifico-OFL.txt"),
        Notice(tr("Rye"), tr("Generated covers."), OFL, tr("© 2011 Sorkin Type Co"), "rye-OFL.txt"),
        Notice(tr("Alfa Slab One"), tr("Generated covers."), OFL, tr("© 2016 The Alfa Slab One Project Authors"), "alfa-slab-one-OFL.txt"),
        Notice(tr("Stardos Stencil"), tr("Generated covers."), OFL, tr("© 2011 Vernon Adams"), "stardos-stencil-OFL.txt"),
        Notice(tr("Bangers"), tr("Generated covers."), OFL, tr("© 2010 The Bangers Project Authors"), "bangers-OFL.txt"),
        Notice(tr("Graduate"), tr("Generated covers."), OFL, tr("© 2012 The Graduate Project Authors"), "graduate-OFL.txt"),
        Notice(tr("Liberation Serif and Sans"), tr("Generated covers, for other alphabets."), OFL, tr("© 2010 Google Corporation, © 2012 Red Hat, Inc."), "liberation-fonts.txt"),
        Notice(tr("EB Garamond"), tr("Reading font, and the app's headings and titles."), OFL, tr("© 2017 The EB Garamond Project Authors"), "eb-garamond-OFL.txt"),
        Notice(tr("Literata"), tr("Reading font."), OFL, tr("© 2017 The Literata Project Authors"), "literata-OFL.txt"),
        Notice(tr("Atkinson Hyperlegible"), tr("Reading font."), OFL, tr("© 2020 Braille Institute of America, Inc."), "atkinson-hyperlegible-OFL.txt"),
        Notice(tr("OpenDyslexic"), tr("Reading font."), OFL, tr("© 2012–2019 Abbie Gonzalez"), "opendyslexic-OFL.txt"),
        Notice(tr("Android Jetpack"), tr("Interface and icons (AndroidX, Jetpack Compose, Material icons)."), APACHE, tr("© The Android Open Source Project"), "apache-2.0.txt"),
        Notice(tr("Kotlin"), tr("Programming language, with kotlinx.coroutines."), APACHE, tr("© JetBrains s.r.o. and Kotlin contributors"), "apache-2.0.txt"),
        Notice("libarchive", tr("CBR and CB7 comics."), tr("BSD 2-Clause License"), tr("© 2003–2018 Tim Kientzle and the libarchive contributors"), "libarchive-BSD.txt"),
        Notice(tr("libarchive for Android"), tr("CBR and CB7 comics."), APACHE, tr("© 2023 Hai Zhang"), "apache-2.0.txt"),
        Notice("Tesseract", tr("Text recognition (OCR) for scanned pages."), APACHE, tr("© Google LLC and the Tesseract contributors"), "apache-2.0.txt"),
        Notice(tr("Tesseract language data"), tr("Language data for Tesseract, downloaded on first use."), APACHE, tr("© Google LLC"), "apache-2.0.txt"),
        Notice("Tesseract4Android", tr("Tesseract for Android."), APACHE, tr("© Robert Pösel, Robert Theis and contributors"), "apache-2.0.txt"),
        Notice("Leptonica", tr("Image handling for Tesseract."), tr("BSD 2-Clause License"), tr("© 2001 Leptonica"), "leptonica-BSD.txt"),
        Notice("libjpeg", tr("JPEG support for Tesseract. This software is based in part on the work of the Independent JPEG Group."), tr("IJG License"), tr("© 1991–2024 Thomas G. Lane, Guido Vollbeding"), "libjpeg-IJG.txt"),
        Notice("libpng", tr("PNG support for Tesseract."), tr("PNG Reference Library License 2"), tr("© 1995–2025 The PNG Reference Library Authors"), "libpng-LICENSE.txt"),
    )

    /** KoLib itself: free software, to share and change under the GPL. */
    val own = Notice("KoLib", tr("This app."), tr("GNU General Public License 3.0"), tr("© 2026 the KoLib authors"), "gpl-3.0.txt")
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
                        tr("Your books stay on your devices and your Kobo. KoLib only goes online to search for covers and to download text recognition data."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        tr("Kobo is a trademark of Rakuten Kobo Inc. This app isn't made or endorsed by Rakuten Kobo."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        tr("The comic reader is modelled on Mihon's. Thanks to the Mihon project."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onShowIntro != null) {
                        androidx.compose.material3.OutlinedButton(onClick = onShowIntro) { Text(tr("Replay introduction")) }
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
