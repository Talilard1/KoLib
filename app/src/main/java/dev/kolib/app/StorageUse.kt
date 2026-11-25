package dev.kolib.app

import java.io.File
import java.nio.file.Files

/** How much of the device KoLib takes up, by kind, worked out by going through its folders. */
class StorageUse(
    /** Each kind of thing KoLib keeps, with the space it takes (only those that take some), in [Kind]'s order. */
    val parts: List<Pair<Kind, Long>>,
    /** The device's storage, and how much of it is free. */
    val total: Long,
    val free: Long,
) {
    enum class Kind { BOOKS, COMICS, COVERS, TEXT, AI, FONTS, BACKUPS, UPDATES, APP, TEMP, OTHER }

    /** All that KoLib takes up. */
    val kolib: Long get() = parts.sumOf { it.second }

    companion object {
        /**
         * What's in KoLib's folders ([data], with [files] and [cache] in it), by kind. Each book's or comic's folder in
         * [library] ([comic] says which it is; null for a folder that isn't one) counts less its cover and the text read
         * from its pages, which count as covers and text recognition; then the text recognition languages, AI models
         * and chats, fonts, the Kobo's database backups and downloaded updates; what's only kept for a while (the
         * [cache], and the web view's own); and whatever else, together (the library's records, the settings, the log).
         * [app] is the app itself; [total] and [free], the device's storage.
         */
        fun measure(
            data: File, files: File, cache: File, library: File, comic: (String) -> Boolean?,
            app: Long, total: Long, free: Long,
        ): StorageUse {
            val sizes = HashMap<Kind, Long>()
            fun add(kind: Kind, bytes: Long) { if (bytes > 0) sizes[kind] = (sizes[kind] ?: 0L) + bytes }
            var counted = 0L
            library.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val isComic = comic(dir.name) ?: return@forEach
                val whole = sizeOf(dir)
                val cover = File(dir, "cover.jpg").takeIf { it.isFile }?.length() ?: 0L
                val text = sizeOf(File(dir, "ocr"))
                add(if (isComic) Kind.COMICS else Kind.BOOKS, whole - cover - text)
                add(Kind.COVERS, cover)
                add(Kind.TEXT, text)
                counted += whole
            }
            listOf(
                Kind.TEXT to File(files, "ocr"), Kind.AI to File(files, "models"), Kind.AI to File(files, "ai"),
                Kind.FONTS to File(files, "fonts"), Kind.BACKUPS to File(files, "backups"), Kind.UPDATES to File(cache, "updates"),
            ).forEach { (kind, f) ->
                val bytes = sizeOf(f)
                add(kind, bytes)
                counted += bytes
            }
            // Kept for a while: the cache (but for the updates, counted already), and what the web view keeps.
            val temp = sizeOf(cache) - sizeOf(File(cache, "updates")) + sizeOf(File(data, "app_webview")) + sizeOf(File(data, "code_cache"))
            add(Kind.TEMP, temp)
            counted += temp
            add(Kind.OTHER, sizeOf(data) - counted)
            add(Kind.APP, app)
            return StorageUse(Kind.entries.mapNotNull { k -> sizes[k]?.let { k to it } }, total, free)
        }

        /** Everything in [f], however deep, not following links out of it. */
        fun sizeOf(f: File): Long =
            if (!f.exists()) 0L
            else f.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.filter { it.isFile }.sumOf { it.length() }
    }
}
