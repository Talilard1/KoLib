# Translations

KoLib is written in English. Every piece of text the app shows goes through `tr("...")` (or
`trc("context", "...")` for a word said differently in different places), and each other language has a
table in `app/src/main/assets/i18n/<language>.json` from the English to its own words. Text with no
translation shows in English. The onboarding's text and the share-menu label are Android string
resources instead (`res/values-<language>/`).

The translations themselves live here, in `tr/*.txt`:

```
@fr
575|Collections
576| · touchez un livre pour voir ses détails
```

Each `@language` starts a section, then `number|text`. The number is the English text's place in
`en_list.json`. `\n` is a line break. Spaces at the start or end are matched to the English
automatically, and French gets its no-break spaces before `: ; ? !` and inside `« »` automatically.

## Writing style

- Labels are short: "Keep screen on", "Tap zone size", "Convert to text".
- A description is one short line, and only where the label isn't enough.
- Messages say what happened and what to do next, nothing more.
- Use the words people know from other apps (default, original, sync, scan) and the same word every
  time. Translations follow the same rules, not the English word for word.

## Adding or changing text

1. Write the text in English in the code, inside `tr(...)`. Whole sentences only: never build a
   sentence from pieces, as other languages put the words in another order. Values go in `{0}`, `{1}`...
   (`tr("Sent {0} of {1}.", done, total)`), and counts get a sentence each for one and for more.
2. `python3 update.py` gives new text its number and writes `build/en_work.txt`, which lists it.
3. Add the translations to a `tr/*.txt` file (a new one is fine).
   Russian has three forms of a word with a count (1 книга, 2 книги, 5 книг; 21 книга), so a Russian
   translation of a count's sentence picks the word with `{0:книга|книги|книг}` (one, few, many) next to
   the number's `{0}`: `{0} {0:книга|книги|книг}`. The app chooses the form for the count it's given.
4. `python3 build.py` checks them: every language has every text, with the same `{n}` and line breaks.
5. `python3 build.py --write` writes the app's tables.

| Code | Language | Address | Kobo |
|---|---|---|---|
| fr | French | vous | la Kobo |
| de | German | du | der Kobo |
| es | Spanish | tú | el Kobo |
| it | Italian | tu | il Kobo |
| nl | Dutch | je | de Kobo |
| pt (pt-BR) | Brazilian Portuguese | você | o Kobo |
| ru | Russian | вы | Kobo (masculine, not declined) |
| ja | Japanese | polite (です・ます) | Kobo |
