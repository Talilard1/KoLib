# KoLib

A library for your Kobo e-reader, on Android.

KoLib keeps your EPUB books on your phone or tablet, sends them to a Kobo over a USB-C cable, and
has its own reader for reading them on the phone or tablet itself. The app has no access to the
internet: your books, and everything you do with them, stay on your device and your Kobo.

## What it does

- **Library:** your books with their covers, series and details, which you can edit. KoLib can
  also make a cover for a book that has none. You can mark books as finished or unread.
- **Kobo:** reads the Kobo's books and collections over a USB-C cable, sends books to it as
  kepubs, and keeps backups of the Kobo's database (a new one whenever you connect and something
  has changed). Tested with a Kobo Clara 2E.
- **Reader:** pages or continuous scrolling, a choice of typefaces, colours and margins, page
  numbers for the whole book, autoscroll, and a button to go back to where you were after
  jumping with the progress bar.

Needs Android 10 or newer.

## Building

You need JDK 17 and the Android SDK (platform 36). The Gradle wrapper fetches the right Gradle.

```
./gradlew assembleDebug            # app/build/outputs/apk/debug/
./gradlew assembleRelease          # app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest   # tests; screenshots go to app/build/outputs/roborazzi/
```

### The signing key

The key that signs release builds is **not** in this repository, and must never be added to it:
anyone with the key could make APKs that install over KoLib as if they were updates.

Without the key, `assembleRelease` makes an unsigned APK. To make signed builds that update the
installed app, put these two files in a `keystore/` folder at the top of the project (git ignores
that folder):

- `release.jks`, the key itself
- `keystore.properties`:

  ```
  storeFile=release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```

Keep a copy of both somewhere safe. If the key is lost, new builds can't update the installed app.

## Project layout

- `app/src/main/java/dev/kobolibrary/app/`: the app (`library`, `kobo`, `epub`, `reader`, `ui`)
- `app/src/main/assets/reader/`: the reader's page code (`reader.js`, `reader.css`)
- `app/src/test/`: tests, including screenshots of every screen
- `tools/opening-sound/`: makes the opening sound, `app/src/main/res/raw/kolib_opening.ogg`

## Licences

No licence has been chosen for KoLib's own code yet, so it isn't open source for now. The parts
that come from elsewhere (fonts, the kepub method, Android libraries) and their licences are listed
in [NOTICE.md](NOTICE.md).

Kobo is a trademark of Rakuten Kobo Inc. KoLib isn't made or endorsed by Rakuten Kobo.
