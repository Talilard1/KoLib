# Building KoLib

For anyone who wants to build KoLib themselves or work on it. To install it, see the
[front page](../README.md#install).

## What you need

- JDK 17 or newer
- The Android SDK with platform 36 (the Gradle wrapper fetches the right Gradle)

## Building and testing

```
./gradlew assembleDebug            # makes app/build/outputs/apk/debug/
./gradlew assembleRelease          # makes app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest   # runs the tests
```

A release build makes an APK for each processor type, and a universal one. The tests include
screenshots of every screen, saved in `app/build/outputs/roborazzi/`.

## The signing key

The key that signs release builds is **not** in this repository, and must never be added to it:
anyone with the key could make APKs that install over KoLib as if they were updates.

Without the key, `assembleRelease` makes unsigned APKs. To make signed builds that update the
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

## Translations

All the app's text goes through `tr("...")`, with a table for each language in
`app/src/main/assets/i18n/`. How to add or change text, and the translations themselves, are in
[tools/i18n](../tools/i18n/README.md).

## Where things are

| Where | What |
|---|---|
| `app/src/main/java/dev/kobolibrary/app/kobo/` | The Kobo: reading its database, sending books, collections, backups of its database |
| `…/library/` | KoLib's own library: books, covers, folders of books |
| `…/epub/` | Reading, checking and editing EPUBs, and making kepubs |
| `…/convert/` | Turning PDF, MOBI, Word and other files into EPUBs |
| `…/ocr/` | Making scanned PDFs into text with Tesseract: the language data, the pages read, the book made from them |
| `…/comic/` | Comic files (CBZ, CBR, CB7) read page by page, and the comic reader's settings |
| `…/reader/` | The reader: pages, settings, highlights |
| `…/covers/` | Looking covers up online |
| `…/ui/` | The screens |
| `app/src/main/assets/reader/` | The reader's page code (`reader.js`, `reader.css`) |
| `app/src/test/` | Tests, including screenshots of every screen |
| `tools/i18n/` | Translations |
| `tools/ocr/` | Makes the scanned test pages for the text recognition tests, `app/src/test/resources/ocr/` |
| `tools/opening-sound/` | Makes the opening sound, `app/src/main/res/raw/kolib_opening.ogg` |
