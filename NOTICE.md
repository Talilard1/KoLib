# KoLib: third-party notices

KoLib's own code is free software under the GNU General Public License, version 3 (see `LICENSE`).
Third-party components and their licences are listed below. Each licence text is in
`app/src/main/assets/licences/` and can be read in the app (Library → ⓘ).

| Component | Used for | Licence | File |
|---|---|---|---|
| kepubify, © 2017–2022 Patrick Gaskin | Kepub conversion: `epub/KepubConverter.kt` follows kepubify's method | MIT | `kepubify-MIT.txt` |
| Lora | Generated covers | SIL OFL 1.1 | `lora-OFL.txt` |
| Poppins | Generated covers | SIL OFL 1.1 | `poppins-OFL.txt` |
| Cinzel, Cinzel Decorative, UnifrakturMaguntia, Great Vibes, Bebas Neue, Orbitron, Poiret One, Fredoka | Generated covers | SIL OFL 1.1 | `cinzel-OFL.txt`, `cinzel-decorative-OFL.txt`, `unifraktur-maguntia-OFL.txt`, `great-vibes-OFL.txt`, `bebas-neue-OFL.txt`, `orbitron-OFL.txt`, `poiret-one-OFL.txt`, `fredoka-OFL.txt` |
| Playfair Display, IM Fell English, Courier Prime, Space Mono, Caveat, Pacifico, Rye, Alfa Slab One, Stardos Stencil, Bangers, Graduate | Generated covers | SIL OFL 1.1 | `playfair-display-OFL.txt`, `im-fell-english-OFL.txt`, `courier-prime-OFL.txt`, `space-mono-OFL.txt`, `caveat-OFL.txt`, `pacifico-OFL.txt`, `rye-OFL.txt`, `alfa-slab-one-OFL.txt`, `stardos-stencil-OFL.txt`, `bangers-OFL.txt`, `graduate-OFL.txt` |
| Liberation Serif and Sans | Generated covers, for other alphabets | SIL OFL 1.1 | `liberation-fonts.txt` |
| EB Garamond, Literata, Atkinson Hyperlegible, OpenDyslexic | Reader fonts; EB Garamond also for headings and book titles | SIL OFL 1.1 | `*-OFL.txt` |
| AndroidX, Jetpack Compose, Material icons, Kotlin, kotlinx.coroutines | App framework | Apache 2.0 | `apache-2.0.txt` |
| libarchive, © 2003–2018 Tim Kientzle and the libarchive contributors | CBR (RAR) and CB7 (7z) comics | BSD 2-clause | `libarchive-BSD.txt` |
| libarchive for Android (`me.zhanghai.android.libarchive`), © 2023 Hai Zhang | libarchive bindings | Apache 2.0 | `apache-2.0.txt` |
| Tesseract 5, © Google LLC and the Tesseract contributors | Text recognition (OCR) for scanned PDFs | Apache 2.0 | `apache-2.0.txt` |
| Tesseract language data (`tessdata_fast` 4.1.0), © Google LLC | OCR language data. Not bundled: downloaded from GitHub on first use | Apache 2.0 | `apache-2.0.txt` |
| Tesseract4Android (`cz.adaptech.tesseract4android`), © Robert Pösel, Robert Theis and contributors | Tesseract bindings; brings in the libraries below | Apache 2.0 | `apache-2.0.txt` |
| Leptonica, © 2001 Leptonica | Image handling for Tesseract | BSD 2-clause | `leptonica-BSD.txt` |
| libjpeg, © 1991–2024 Thomas G. Lane, Guido Vollbeding. This software is based in part on the work of the Independent JPEG Group | JPEG support for Tesseract | IJG | `libjpeg-IJG.txt` |
| libpng, © 1995–2025 The PNG Reference Library Authors | PNG support for Tesseract | PNG Reference Library License 2 | `libpng-LICENSE.txt` |
| llama.cpp, © 2023–2026 The ggml authors, with the libraries it includes (their licences are in `third_party/llama.cpp/licenses`) | Runs AI models on the device (64-bit builds) | MIT | `llama.cpp-MIT.txt` |
| AI models: Gemma 4 E2B and E4B (© Google), Qwen3.5 0.8B (© Alibaba Cloud) | Not bundled: downloaded from Hugging Face when the user asks for one | Apache 2.0 | `apache-2.0.txt` |
| Wiktionary, © its contributors | Dictionary entries. Not bundled: fetched from en.wiktionary.org when a word is looked up | CC BY-SA 4.0 | https://creativecommons.org/licenses/by-sa/4.0/ |
| ZXing (`com.google.zxing:core`), © ZXing authors | QR codes for pairing devices | Apache 2.0 | `apache-2.0.txt` |
| CameraX (`androidx.camera`), © The Android Open Source Project | The camera, for scanning a pairing QR code | Apache 2.0 | `apache-2.0.txt` |

Test-only libraries (JUnit, Robolectric, Roborazzi, sqlite-jdbc) are not part of the app.

## What was learned from Calibre

Calibre (GPL v3) is not used, and none of its code is in the app. Its KoboTouch driver was read to
learn how a Kobo stores its books: the tables and columns of `KoboReader.sqlite` and what their
values mean (the content types, the Accessibility codes that tell bought books, previews and
recommendations apart, collections in `Shelf` and `ShelfContent`, the timestamp format), the
`.kobo/version` file, the USB and model ids, and where the Kobo keeps its cover pictures (the
`.kobo-images` folders picked by Qt 4's string hash, the pictures' names, and the boxes they're
sized for). These are facts about the Kobo's own formats, checked against a Kobo Clara 2E, and the
code that uses them was written for this app.

The app also reads and writes the metadata names Calibre puts in EPUB files (`calibre:series`,
`calibre:title_sort`, `calibre:rating`), so books stay readable by both, and files books on the
Kobo as `Author Sort/Title - Authors`, an arrangement Calibre users will recognise.

Up to version 0.8.2 the cover maker was a port of Calibre's cover generator
(`calibre/ebooks/covers.py`, GPL v3). Version 0.8.3 replaced it with designs of the app's own.

## What was learned from Mihon

Mihon (Apache 2.0, https://github.com/mihonapp/mihon) is not used, and none of its code is in the
app. KoLib's comic reader is modelled on Mihon's: its reading modes, ways of fitting pages, cropping
borders and splitting wide pages, its tap zones, and how its pages are zoomed (pinching, double
tapping to twice the size with the spot tapped brought to the middle, and double tapping then
sliding up or down). Mihon's reader was read to see how it behaves, and the comic reader was
written for this app. Thanks to the Mihon project.

## Trademarks

Kobo is a trademark of Rakuten Kobo Inc. This app isn't made or endorsed by Rakuten Kobo.
