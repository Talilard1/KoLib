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
        "You are a reading companion in an e-book app, like a well-read friend. With a question you may be given notes: what the reader " +
            "has read of a book so far, or a list of the books in their library. Treat them as things you remember, and never mention " +
            "notes, passages or text you were given. You may also use what you know of a book in general: what kind of book it is and " +
            "what it sets out to do. Never reveal anything that happens later in a book than where the reader is, unless they ask you to. " +
            "If you don't know, say so. Keep answers short: a few sentences, plain words, no markdown."

    private fun instructions(custom: String) = custom.trim().ifEmpty { CHAT_DEFAULT }

    /** The chat's instructions for a question about the book being read: the reader's own or the usual ones, then where they are in it. */
    fun chatSystem(title: String, author: String?, chapter: String, percent: Int, answerIn: String, about: String? = null, custom: String = ""): AiMessage = AiMessage.system(
        instructions(custom) + "\n\n" +
            "The reader is reading ${book(title, author)} and is $percent% of the way through" + (if (chapter.isBlank()) "" else ", in “$chapter”") + ". " +
            (about?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.length > 40 }?.let { "Its description: " + (if (it.length > 900) it.take(900) + "…" else it) + " " } ?: "") +
            "Answer in $answerIn."
    )

    /** For questions asked from the library rather than from inside a book. */
    fun librarySystem(books: Int, answerIn: String, custom: String = ""): AiMessage = AiMessage.system(
        instructions(custom) + "\n\n" +
            "The reader is in their library, which has $books " + (if (books == 1) "book" else "books") + ". The list you are given has each book's title, " +
            "author, series, tags, and how far they've read where that's known. For questions about what they have, go by that list and don't add " +
            "books that aren't on it. Answer in $answerIn."
    )

    /** For a question asked in a book with the reading of it turned off: the model answers from what it knows. */
    fun plainSystem(title: String, author: String?, answerIn: String, custom: String = ""): AiMessage = AiMessage.system(
        instructions(custom) + "\n\n" +
            "The reader is reading ${book(title, author)}. You have not been shown any of it this time: answer from what you know. Answer in $answerIn."
    )

    /** A question about the library, with the [list] of its books (and how many more there wasn't room for). */
    fun libraryQuestion(question: String, list: String, left: Int): AiMessage = AiMessage.user(
        buildString {
            if (list.isNotBlank()) {
                append("Books in the library:\n").append(list.trim())
                if (left > 0) append("\n(and ").append(left).append(" more not listed)")
                append("\n\n")
            }
            append("Question: ").append(question.trim())
        }
    )

    /** A question, with what of the book bears on it ([passages]) and the [latest] lines the reader has read. */
    fun chatQuestion(question: String, passages: String, latest: String): AiMessage = AiMessage.user(
        buildString {
            if (passages.isNotBlank()) append("What you remember of the book so far:\n").append(passages.trim()).append("\n\n")
            if (latest.isNotBlank()) append("The pages the reader is on now:\n").append(latest.trim()).append("\n\n")
            append("Question: ").append(question.trim())
        }
    )
}
