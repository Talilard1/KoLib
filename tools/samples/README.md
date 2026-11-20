# The sample books

KoLib comes with three classics and a short comic (app/src/main/assets/samples), to try it with. These
scripts make them, in tools/samples/build:

1. The texts: NLTK's Gutenberg corpus (gutenberg.zip from nltk_data), unzipped.
2. The covers: list them in covers.tsv (file, style, look, genre, title, authors, width, height), make
   the folder tools/samples/build, then run the test `dev.kolib.app.SampleBookCovers`. It draws them with
   KoLib's own cover designer, into build/covers.
3. `python3 make_epubs.py <the corpus folder>` makes the EPUBs, with those covers, in build/epub.
4. `python3 make_comic.py` draws The Lighthouse Cat and packs it as build/lighthouse-cat.cbz.

Then copy the four files into app/src/main/assets/samples.

The texts are in the public domain. The covers and the comic are part of KoLib, under its licence.
