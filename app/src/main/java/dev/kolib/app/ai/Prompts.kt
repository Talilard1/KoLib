package dev.kolib.app.ai

import java.util.Locale

/** What the model is told and asked, for each thing KoLib uses it for. Written in English; the answers come in the reader's language. */
object Prompts {

    /** A language's English name, from its code: "fr" → "French". */
    fun languageName(code: String?): String {
        val c = code?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
        if (c.isEmpty()) return "English"
        return Locale.forLanguageTag(c).getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) } ?: "English"
    }

    private fun book(title: String, author: String?) = if (author.isNullOrBlank()) "“$title”" else "“$title” by $author"

    /** What [word] means where it's used, in [sentence]: its answer is two lines, "Here: …" and "Usually: …". */
    fun lookUp(word: String, sentence: String, title: String, author: String?, answerIn: String): List<AiMessage> = listOf(
        AiMessage.system(
            "You are the dictionary inside an e-book reader. A reader has tapped a word or phrase in a book and wants to know what it " +
                "means where they are reading. Answer in $answerIn. Be accurate, plain and brief."
        ),
        AiMessage.user(
            "From ${book(title, author)}:\n\"$sentence\"\n\n" +
                "Explain \"$word\" as it is used in this sentence. Do not use the word itself to explain it. " +
                "Reply in exactly this form, two lines and nothing else:\n" +
                "$HERE <one sentence saying what it means in this sentence>\n" +
                "$USUALLY <its part of speech>, <its general dictionary meaning in a few words>"
        ),
    )

    const val HERE = "Here:"
    const val USUALLY = "Usually:"

    /** A look-up's answer in its two parts: what the word means here, and what it usually means (either may be empty). */
    fun readLookUp(answer: String): Pair<String, String> {
        val text = cleanAnswer(answer)
        val u = text.indexOf(USUALLY)
        val here = (if (u >= 0) text.substring(0, u) else text).trim().removePrefix(HERE).trim()
        val usually = if (u >= 0) text.substring(u + USUALLY.length).trim() else ""
        return here to usually
    }

    /** A passage put in plain words. */
    fun passage(passage: String, title: String, author: String?, answerIn: String): List<AiMessage> = listOf(
        AiMessage.system(
            "You help a reader understand a book they're reading in an e-book reader. Answer in $answerIn. Be accurate, plain and brief. " +
                "No preamble, no markdown."
        ),
        AiMessage.user("From ${book(title, author)}:\n\"$passage\"\n\nSay what this passage means in plain words, in two or three sentences. Explain any unusual word in it."),
    )

    private const val SUMMARY_RULES =
        "Use only what the text says; add nothing from outside it and nothing that happens later in the book. No preamble, no markdown headings."

    /** Notes on one piece of a chapter too long to read in one go: piece [part] of [parts]. */
    fun chapterPart(text: String, chapter: String, title: String, part: Int, parts: Int, answerIn: String): List<AiMessage> = listOf(
        AiMessage.system("You help a reader keep track of a book. Answer in $answerIn. $SUMMARY_RULES"),
        AiMessage.user(
            "This is part $part of $parts of ${chapterName(chapter)} of “$title”:\n\n$text\n\n" +
                "List what happens in this part as 3 to 6 short bullet points, in order: who does or says what, and anything a reader should remember. Start each with \"- \"."
        ),
    )

    /** The chapter's summary, from its whole [text] or from the notes on its parts ([fromNotes]). [soFar]: only up to where the reader is. */
    fun chapter(text: String, chapter: String, title: String, fromNotes: Boolean, soFar: Boolean, answerIn: String): List<AiMessage> = listOf(
        AiMessage.system("You help a reader keep track of a book. Answer in $answerIn. $SUMMARY_RULES"),
        AiMessage.user(
            (if (fromNotes) "These are notes on the parts of ${chapterName(chapter)} of “$title”, in order" else "This is ${chapterName(chapter)} of “$title”") +
                (if (soFar) ", up to where the reader has read" else "") + ":\n\n$text\n\n" +
                "Summarise it for the reader: first two or three sentences on what happens overall, then 3 to 6 short bullet points of the main events in order, each starting with \"- \"."
        ),
    )

    private fun chapterName(chapter: String) = if (chapter.isBlank()) "a chapter" else "the chapter “$chapter”"

    /**
     * How the chat behaves, unless the user has written instructions of their own: these are what they see
     * and can change. What the reader is reading and where they are in it is told to the model after them.
     */
    const val CHAT_DEFAULT =
        "You are a reading companion in an e-book app, like a well-read friend. You may be given what the reader has read of a book " +
            "so far (the page on their screen, the pages before it, notes on earlier chapters) or a list of the books in their library. " +
            "Treat it as what you remember and can see, and never mention notes, passages or text you were given. When the reader says " +
            "\"this page\", \"here\" or \"this\", they mean the page on their screen. You may also use what you know of a book in general: " +
            "what kind of book it is and what it sets out to do. Never reveal anything that happens later in a book than where the reader " +
            "is, unless they ask you to. If you don't know, say so. Keep answers short: a few sentences, plain words, no markdown."

    private fun instructions(custom: String) = custom.trim().ifEmpty { CHAT_DEFAULT }

    /**
     * The chat's instructions for a question about the book being read: the reader's own or the usual ones, where
     * they are in it, and what they've read: [notes] on earlier chapters, the pages just [before] the one on their
     * screen, and that page ([screen]). It stays the same while the reader stays on the page, so the model keeps
     * it read from one question to the next.
     */
    fun chatSystem(
        title: String, author: String?, chapter: String, percent: Int, answerIn: String, about: String? = null, custom: String = "",
        notes: String = "", before: String = "", screen: String = "",
    ): AiMessage = AiMessage.system(
        buildString {
            append(instructions(custom)).append("\n\n")
            append("The reader is reading ${book(title, author)} and is $percent% of the way through").append(if (chapter.isBlank()) "" else ", in “$chapter”").append(". ")
            about?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.length > 40 }?.let { append("Its description: ").append(if (it.length > 900) it.take(900) + "…" else it).append(" ") }
            if (notes.isNotBlank()) append("\n\nYour notes on the chapters before this one:\n").append(notes.trim())
            if (screen.isNotBlank()) {
                if (before.isNotBlank()) append("\n\nWhat the reader read just before this page:\n").append(before.trim())
                append("\n\nThe page on the reader's screen now:\n").append(screen.trim())
            } else if (before.isNotBlank()) {
                append("\n\nThe pages the reader is on now:\n").append(before.trim())
            }
            append("\n\nAnswer in $answerIn.")
        }
    )

    /**
     * For questions asked from the library rather than from inside a book: what's on the reader's [screen] there,
     * and the [list] of the library's books (and how many more there wasn't room for). Like a book's, it stays the
     * same from one question to the next while the screen does.
     */
    fun librarySystem(books: Int, answerIn: String, custom: String = "", screen: String = "", list: String = "", left: Int = 0): AiMessage = AiMessage.system(
        buildString {
            append(instructions(custom)).append("\n\n")
            append("The reader is in their library, which has $books ").append(if (books == 1) "book" else "books").append(". The list below has each book's title, ")
            append("author, series, tags, and how far they've read where that's known. For questions about what they have, go by that list and don't add ")
            append("books that aren't on it.")
            if (screen.isNotBlank()) append("\n\nOn the reader's screen now: ").append(screen.trim())
            if (list.isNotBlank()) {
                append("\n\nBooks in the library:\n").append(list.trim())
                if (left > 0) append("\n(and ").append(left).append(" more not listed)")
            }
            append("\n\nAnswer in $answerIn.")
        }
    )

    /** For a question asked in a book with the reading of it turned off: the model answers from what it knows. */
    fun plainSystem(title: String, author: String?, answerIn: String, custom: String = ""): AiMessage = AiMessage.system(
        instructions(custom) + "\n\n" +
            "The reader is reading ${book(title, author)}. You have not been shown any of it this time: answer from what you know. Answer in $answerIn."
    )

    /** A question about the library, with any books that bear on it that the list left out ([more]). */
    fun libraryQuestion(question: String, more: String = ""): AiMessage = AiMessage.user(
        buildString {
            if (more.isNotBlank()) append("More books in the library that may bear on this:\n").append(more.trim()).append("\n\n")
            append("Question: ").append(question.trim())
        }
    )

    /**
     * A question about the book, with what else of it bears on the question ([passages]) and, when the reader has
     * picked out some words on the page to ask about, those ([selected]).
     */
    fun chatQuestion(question: String, passages: String, selected: String = ""): AiMessage = AiMessage.user(
        buildString {
            if (passages.isNotBlank()) append("What you remember of the book so far:\n").append(passages.trim()).append("\n\n")
            if (selected.isNotBlank()) append("The reader has picked out these words on the page:\n“").append(selected.trim()).append("”\n\n")
            append("Question: ").append(question.trim())
        }
    )

    /**
     * Notes for the model's own memory on a chapter the reader has finished, from its [text] or from the notes on
     * its parts ([fromNotes]). Written in English, whatever the book's language: they're for the model to read.
     */
    fun chapterNote(text: String, chapter: String, title: String, fromNotes: Boolean): List<AiMessage> = listOf(
        AiMessage.system("You keep brief notes on a book for someone reading it, to remind them of it later. Use only what the text says. No preamble, no markdown. Write in English."),
        AiMessage.user(
            (if (fromNotes) "These are notes on the parts of ${chapterName(chapter)} of “$title”, in order" else "This is ${chapterName(chapter)} of “$title”") + ":\n\n$text\n\n" +
                "Write a note on it of at most 90 words: who appears (with a few words on each, by name), what happens, and anything that changes. Plain sentences."
        ),
    )
}
