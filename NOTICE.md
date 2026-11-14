# KoLib: third-party notices

The app's own code and designs are not open source. These are the parts that come from elsewhere,
and the terms they're used under. Each licence is in `app/src/main/assets/licences/`, and can be
read in the app (Library, then the ⓘ button).

| What | Used for | Licence | File |
|---|---|---|---|
| kepubify, © 2017–2022 Patrick Gaskin | Sending books as kepubs: `epub/KepubConverter.kt` follows kepubify's method | MIT | `kepubify-MIT.txt` |
| Lora | Lettering on covers the app makes | SIL OFL 1.1 | `lora-OFL.txt` |
| Poppins | Lettering on covers the app makes | SIL OFL 1.1 | `poppins-OFL.txt` |
| Liberation Serif and Sans | Covers the app makes, for alphabets Lora and Poppins lack | SIL OFL 1.1 | `liberation-fonts.txt` |
| EB Garamond, Literata, Atkinson Hyperlegible, OpenDyslexic | Typefaces in the reader; EB Garamond also for the app's headings and book titles | SIL OFL 1.1 | `*-OFL.txt` |
| AndroidX, Jetpack Compose, Material icons, Kotlin, kotlinx.coroutines | The app's framework | Apache 2.0 | `apache-2.0.txt` |

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

## Trademarks

Kobo is a trademark of Rakuten Kobo Inc. This app isn't made or endorsed by Rakuten Kobo.
