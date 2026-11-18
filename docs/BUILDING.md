# Building KoLib

To install KoLib, see [Download](../README.md#download). This page is for building it from source.

## What you need

- JDK 17 or newer
- The Android SDK with platform 36 (the Gradle wrapper fetches the right Gradle)

## Building and testing

```
./gradlew assembleDebug            # app/build/outputs/apk/debug/
./gradlew assembleRelease          # app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest   # tests
```

A release build produces one APK per ABI and a universal one. The tests include screenshots of
every screen, saved in `app/build/outputs/roborazzi/`.

## The AI engine

The part that runs AI models on the device is [llama.cpp](https://github.com/ggml-org/llama.cpp)
(a git submodule in `third_party/llama.cpp`) with a small layer of KoLib's own in `app/src/main/cpp`.
It's built once, outside Gradle, for 64-bit devices (arm64-v8a and x86_64):

```
git submodule update --init --depth 1
ANDROID_NDK=/path/to/android-sdk/ndk/29.0.14206865 tools/llama/build-android.sh
```

That needs the Android NDK, CMake 3.22 or newer and Ninja, and puts the libraries in
`app/src/main/jniLibs/`, where the app's build picks them up. Run it again after changing
`app/src/main/cpp` or updating llama.cpp.

Without those libraries KoLib still builds and runs; it just can't run models itself, and says so.
`tools/llama/try.cpp` runs the same layer on a computer, for trying a model and a prompt.

## The signing key

The release signing key is **not** in this repository and must never be added to it: anyone with
the key could publish APKs that install as updates to KoLib.

Without the key, `assembleRelease` produces unsigned APKs. For signed builds, put these two files
in a `keystore/` folder at the top of the project (ignored by git):

- `release.jks`, the key itself
- `keystore.properties`:

  ```
  storeFile=release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```

Back up both. If the key is lost, new builds can't update the installed app.

## Translations

All text goes through `tr("...")`, with one table per language in `app/src/main/assets/i18n/`.
See [tools/i18n](../tools/i18n/README.md) to add or change text.

## Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/dev/kolib/app/kobo/` | Kobo support: database, sending books, collections, backups |
| `…/library/` | The library: books, covers, book folders |
| `…/epub/` | Reading, validating and editing EPUBs; kepub conversion |
| `…/convert/` | Converting PDF, MOBI, Word and other files to EPUB |
| `…/ocr/` | OCR for scanned PDFs with Tesseract |
| `…/comic/` | Comic files (CBZ, CBR, CB7) and comic reader settings |
| `…/reader/` | The reader: pages, settings, highlights |
| `…/covers/` | Online cover search |
| `…/ui/` | Screens |
| `app/src/main/assets/reader/` | Reader page code (`reader.js`, `reader.css`) |
| `app/src/test/` | Tests, including screenshots of every screen |
| `tools/i18n/` | Translations |
| `tools/ocr/` | Generates the scanned test pages in `app/src/test/resources/ocr/` |
| `tools/opening-sound/` | Generates the opening sound, `app/src/main/res/raw/kolib_opening.ogg` |
