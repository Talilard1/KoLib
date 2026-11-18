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

    /** How the companion behaves, for a conversation about the book. */
    fun chatSystem(title: String, author: String?, chapter: String, percent: Int, answerIn: String): AiMessage = AiMessage.system(
        "You are a reading companion inside an e-book reader. The reader is reading ${book(title, author)} and is $percent% of the way through" +
            (if (chapter.isBlank()) "" else ", in the chapter “$chapter”") + ". " +
            "With each question you are given passages from the part of the book they have read. Answer from those passages. " +
            "Never give away anything that happens later in the book than where the reader is. " +
            "If the passages don't answer the question, say so and tell them what the passages do show. " +
            "Keep answers short: a few sentences, plain words, no markdown. Answer in $answerIn."
    )

    /** A question, with the [passages] of the book that bear on it and the [latest] lines the reader has read. */
    fun chatQuestion(question: String, passages: String, latest: String): AiMessage = AiMessage.user(
        buildString {
            if (passages.isNotBlank()) append("Passages from the book so far:\n").append(passages.trim()).append("\n\n")
            if (latest.isNotBlank()) append("Where the reader is now:\n").append(latest.trim()).append("\n\n")
            append("Question: ").append(question.trim())
        }
    )
}
