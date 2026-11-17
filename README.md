<p align="center">
  <img src="docs/images/icon.png" width="96" alt="KoLib icon">
</p>

<h1 align="center">KoLib</h1>

<p align="center">
  <b>An ebook library and Kobo manager for Android.</b><br>
  Organise your books, send them to your Kobo, read them and edit them.
</p>

<p align="center">
  <img alt="Android 10 or newer" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square">
  <img alt="9 languages" src="https://img.shields.io/badge/languages-9-C8963E?style=flat-square">
  <img alt="No account, no ads" src="https://img.shields.io/badge/no%20account-no%20ads-6B4A2E?style=flat-square">
  <img alt="Licence: GPL 3.0" src="https://img.shields.io/badge/licence-GPL%203.0-2F6DB5?style=flat-square">
</p>

<p align="center">
  <img src="docs/images/tablet-library.webp" width="860" alt="The library on a tablet">
</p>

## Download

Get the latest APK from [Releases](https://github.com/Talilard1/KoLib/releases/latest).

| File | Best for |
|---|---|
| **KoLib-…-arm64-v8a.apk** | **Almost every phone and tablet from the last several years** |
| KoLib-…-armeabi-v7a.apk | Older 32-bit phones and tablets |
| KoLib-…-x86_64.apk | Chromebooks, and Android emulators on a PC |
| KoLib-…-x86.apk | Older emulators |
| KoLib-…-universal.apk | If you are uncertain |

*Requires Android 10 or higher. Sending over USB needs a USB-C data cable.*

## Features

- A library for EPUB, kepub, PDF, MOBI, AZW3, FB2, Word, OpenDocument, RTF, text, Markdown and HTML
  books, and CBZ, CBR and CB7 comics.
- Send books to a Kobo over USB as kepub or EPUB, straight into your Kobo collections, with covers
  sized for its screen.
- Send over Wi-Fi through the Kobo's web browser when there's no cable to hand.
- A configurable reader: fonts, page colours, paged or scrolling, two-page view, print-style
  layout, highlights, notes, bookmarks, dictionary lookup and autoscroll.
- A comic reader modelled on [Mihon](https://github.com/mihonapp/mihon)'s.
- Reading stats, including the reading time recorded by your Kobo.
- Edit titles, authors, series, tags and covers. Find covers online, or generate one from 123
  designs in 43 colour schemes.
- Edit a book's text: find and replace, chapter tools, clean-ups, and splitting or combining books.
- Convert Word, PDF, MOBI, FB2, text and comic files to EPUB, and scanned PDFs to text with
  on-device OCR.
- Sync books and reading position between your phone and tablet over Wi-Fi. Pair them with a QR code.
- Light, dark, OLED and bookcase themes.
- English, Français, Deutsch, Español, Italiano, Nederlands, Português (Brasil), Русский and 日本語.
- No account, no ads, no tracking.

## Screenshots

<table>
  <tr>
    <td align="center" width="50%"><a href="docs/images/tablet-kobo-books.webp"><img src="docs/images/tablet-kobo-books.webp" alt="The books on a Kobo"></a><br><b>Books on your Kobo</b></td>
    <td align="center" width="50%"><a href="docs/images/tablet-kobo-collections.webp"><img src="docs/images/tablet-kobo-collections.webp" alt="A Kobo's collections"></a><br><b>Collections</b></td>
  </tr>
  <tr>
    <td align="center" width="50%"><a href="docs/images/tablet-send.webp"><img src="docs/images/tablet-send.webp" alt="Sending books to a Kobo"></a><br><b>Send to Kobo</b></td>
    <td align="center" width="50%"><a href="docs/images/tablet-details.webp"><img src="docs/images/tablet-details.webp" alt="Editing a book's details"></a><br><b>Edit details</b></td>
  </tr>
  <tr>
    <td align="center" width="50%"><a href="docs/images/tablet-cover.webp"><img src="docs/images/tablet-cover.webp" alt="Changing a cover"></a><br><b>Change a cover</b></td>
    <td align="center" width="50%"><a href="docs/images/tablet-make-cover.webp"><img src="docs/images/tablet-make-cover.webp" alt="The cover generator"></a><br><b>Cover generator</b></td>
  </tr>
  <tr>
    <td align="center" width="50%"><a href="docs/images/tablet-reader.webp"><img src="docs/images/tablet-reader.webp" alt="Two pages side by side in the reader"></a><br><b>Reader</b></td>
    <td align="center" width="50%"><a href="docs/images/tablet-reader-settings.webp"><img src="docs/images/tablet-reader-settings.webp" alt="The reader's settings"></a><br><b>Reader settings</b></td>
  </tr>
  <tr>
    <td align="center" width="50%"><a href="docs/images/tablet-editor.webp"><img src="docs/images/tablet-editor.webp" alt="Find and replace in the text editor"></a><br><b>Text editor</b></td>
    <td align="center" width="50%"><a href="docs/images/tablet-library-light.webp"><img src="docs/images/tablet-library-light.webp" alt="The library in the light theme"></a><br><b>Light theme</b></td>
  </tr>
</table>

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/images/phone-library.webp" alt="The library on a phone"><br><b>Library</b></td>
    <td align="center" width="33%"><img src="docs/images/phone-book.webp" alt="A book's page"><br><b>Book details</b></td>
    <td align="center" width="33%"><img src="docs/images/phone-kobo.webp" alt="A Kobo's books"><br><b>Kobo</b></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/phone-send.webp" alt="Sending a book"><br><b>Send to Kobo</b></td>
    <td align="center" width="33%"><img src="docs/images/phone-reader.webp" alt="The reader"><br><b>Reader</b></td>
    <td align="center" width="33%"><img src="docs/images/phone-make-cover.webp" alt="The cover generator"><br><b>Cover generator</b></td>
  </tr>
</table>

## Connecting a Kobo

1. Plug the Kobo into your phone or tablet with a USB-C data cable.
2. Tap **Connect** on the Kobo.
3. The first time, Android asks which folder KoLib may use. Tap **Use this folder**, then **Allow**.

When you're done, tap **Eject** in KoLib and unmount the Kobo before unplugging it.

KoLib can't create collections on a Kobo. Create them on the Kobo first
(My Books → Collections → New collection).

Tested with a Kobo Clara 2E. Other Kobo e-readers store books the same way and should work.

## Privacy

Your books stay on your devices and your Kobo. KoLib only goes online to search for covers and to
download OCR language data. The camera is used only to scan a pairing QR code.

## Reporting a problem

Open an [issue](https://github.com/Talilard1/KoLib/issues), say which Kobo and device you have, and
attach the log (Kobo → Device → Open log).

## Credits

- [Mihon](https://github.com/mihonapp/mihon): the comic reader is modelled on Mihon's.
- [kepubify](https://github.com/pgaskin/kepubify): kepub conversion follows its method.
- [libarchive](https://www.libarchive.org/) and [libarchive-android](https://github.com/zhanghai/libarchive-android):
  CBR and CB7 comics.
- [Tesseract](https://github.com/tesseract-ocr/tesseract) and [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android):
  OCR.
- [ZXing](https://github.com/zxing/zxing) and [CameraX](https://developer.android.com/media/camera/camerax): QR codes
  for pairing devices.

Fonts and all licences are listed in [NOTICE.md](NOTICE.md).

## Building

See [docs/BUILDING.md](docs/BUILDING.md).

## Licence

KoLib is free software under the GNU General Public License, version 3 ([LICENSE](LICENSE)).

Kobo is a trademark of Rakuten Kobo Inc. KoLib isn't made by, endorsed by or connected with
Rakuten Kobo. The books in the screenshots are public domain, with covers made from public-domain paintings.
