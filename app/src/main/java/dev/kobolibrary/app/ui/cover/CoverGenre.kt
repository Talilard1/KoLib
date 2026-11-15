package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.tr
import java.util.Locale

/**
 * What sort of book it is, for a cover's picture and the designs that suit it. Worked out from the
 * book's tags, series, title and description ([guess]), and can be changed when making a cover.
 */
enum class CoverGenre(val label: String) {
    FANTASY(tr("Fantasy")),
    SCIENCE_FICTION(tr("Science fiction")),
    MYSTERY(tr("Mystery and crime")),
    THRILLER(tr("Thriller")),
    HORROR(tr("Horror")),
    ROMANCE(tr("Romance")),
    HISTORICAL(tr("Historical")),
    ADVENTURE(tr("Adventure")),
    WESTERN(tr("Western")),
    LITERARY(tr("Literary and classics")),
    NON_FICTION(tr("Non-fiction")),
    CHILDREN(tr("Children's")),
    ;

    companion object {
        /** Words that say what a book is, each with how surely; the book's tags count for most. */
        private val words: Map<CoverGenre, List<String>> = mapOf(
            FANTASY to listOf(
                "fantasy", "epic fantasy", "high fantasy", "sword and sorcery", "magic", "wizard", "witch", "sorcer", "dragon", "elves",
                "elf", "fae", "faerie", "fairy tale", "myth", "legend", "quest", "kingdom", "realm", "throne", "crown", "prophecy",
                "litrpg", "progression fantasy", "cultivation", "grimdark", "urban fantasy", "isekai",
            ),
            SCIENCE_FICTION to listOf(
                "science fiction", "sci-fi", "scifi", "sf", "space opera", "space", "starship", "galaxy", "galactic", "planet", "alien",
                "robot", "android", "cyberpunk", "dystopia", "dystopian", "post-apocalyptic", "apocalypse", "time travel", "future",
                "military science fiction", "mars", "interstellar", "empire", "jedi", "sith", "clone",
            ),
            MYSTERY to listOf(
                "mystery", "mysteries", "detective", "crime", "murder", "whodunit", "police procedural", "noir", "private investigator",
                "sleuth", "cozy mystery", "cosy mystery", "inspector", "case",
            ),
            THRILLER to listOf(
                "thriller", "thrillers", "suspense", "spy", "espionage", "conspiracy", "assassin", "terror", "legal thriller",
                "political thriller", "psychological thriller", "action", "techno-thriller", "military thriller",
            ),
            HORROR to listOf(
                "horror", "ghost", "haunted", "haunting", "vampire", "zombie", "werewolf", "demon", "occult", "supernatural", "gothic",
                "monster", "cthulhu", "lovecraft", "dark fiction", "undead",
            ),
            ROMANCE to listOf(
                "romance", "romantic", "love story", "love", "regency", "romcom", "rom-com", "historical romance", "contemporary romance",
                "paranormal romance", "wedding", "bride", "duke", "heart",
            ),
            HISTORICAL to listOf(
                "historical fiction", "historical", "history", "ancient", "rome", "roman", "greek", "medieval", "tudor", "victorian",
                "napoleonic", "world war", "civil war", "viking", "empire", "war",
            ),
            ADVENTURE to listOf(
                "adventure", "adventures", "pirate", "pirates", "sea", "ocean", "voyage", "treasure", "expedition", "explorer", "jungle",
                "survival", "nautical", "naval",
            ),
            WESTERN to listOf("western", "westerns", "cowboy", "frontier", "outlaw", "ranch", "gunslinger", "wild west", "sheriff"),
            LITERARY to listOf(
                "literary fiction", "literature", "classic", "classics", "fiction", "novel", "poetry", "poems", "drama", "plays",
                "short stories", "essays",
            ),
            NON_FICTION to listOf(
                "non-fiction", "nonfiction", "biography", "autobiography", "memoir", "science", "philosophy", "politics", "economics",
                "business", "self-help", "psychology", "religion", "theology", "cookery", "cooking", "travel", "reference", "textbook",
                "language", "grammar", "dictionary", "lexicon", "mathematics", "programming", "health",
            ),
            CHILDREN to listOf(
                "children", "children's", "childrens", "kids", "juvenile", "picture book", "middle grade", "young adult", "ya",
                "teen", "bedtime", "nursery",
            ),
        )

        /** Series well enough known to say what they are, whatever their books are tagged. */
        private val series: Map<String, CoverGenre> = mapOf(
            "star wars" to SCIENCE_FICTION, "star trek" to SCIENCE_FICTION, "warhammer 40" to SCIENCE_FICTION, "dune" to SCIENCE_FICTION,
            "foundation" to SCIENCE_FICTION, "the expanse" to SCIENCE_FICTION, "halo" to SCIENCE_FICTION, "mass effect" to SCIENCE_FICTION,
            "wheel of time" to FANTASY, "stormlight" to FANTASY, "mistborn" to FANTASY, "cosmere" to FANTASY, "discworld" to FANTASY,
            "harry potter" to FANTASY, "witcher" to FANTASY, "lord of the rings" to FANTASY, "middle-earth" to FANTASY, "earthsea" to FANTASY,
            "song of ice and fire" to FANTASY, "dragonlance" to FANTASY, "forgotten realms" to FANTASY, "malazan" to FANTASY,
            "narnia" to FANTASY, "shannara" to FANTASY, "sword of truth" to FANTASY, "first law" to FANTASY, "kingkiller" to FANTASY,
            "realm of the elderlings" to FANTASY, "riftwar" to FANTASY, "warhammer" to FANTASY, "dresden" to FANTASY,
            "sherlock holmes" to MYSTERY, "hercule poirot" to MYSTERY, "poirot" to MYSTERY, "miss marple" to MYSTERY, "maigret" to MYSTERY,
            "jack reacher" to THRILLER, "bourne" to THRILLER, "james bond" to THRILLER, "jack ryan" to THRILLER, "mitch rapp" to THRILLER,
            "hornblower" to ADVENTURE, "aubrey" to ADVENTURE, "sharpe" to HISTORICAL, "outlander" to ROMANCE, "bridgerton" to ROMANCE,
        )

        private fun normal(s: String?) = s.orEmpty().lowercase(Locale.ROOT)

        private fun contains(text: String, word: String): Boolean {
            if (word.length <= 3) return Regex("(^|[^a-z])" + Regex.escape(word) + "([^a-z]|$)").containsMatchIn(text)
            return text.contains(word)
        }

        /** The likeliest genre for a book with these [tags], [series], [title] and [description]; null if nothing says. */
        fun guessOrNull(tags: List<String>, series: String?, title: String?, description: String?): CoverGenre? {
            val s = normal(series)
            if (s.isNotEmpty()) this.series.entries.firstOrNull { s.contains(it.key) }?.let { return it.value }
            // In a title, only as words of their own ("Dune", not "Dunedin").
            val t = normal(title)
            this.series.entries.firstOrNull { Regex("(^|[^a-z])" + Regex.escape(it.key) + "([^a-z]|$)").containsMatchIn(t) }?.let { return it.value }
            val score = HashMap<CoverGenre, Int>()
            val tagText = tags.map(::normal)
            val about = normal(description).take(3000)
            for ((genre, list) in words) {
                var n = 0
                for (w in list) {
                    // A tag that is just the word counts most; a word in a tag, the title or the description less.
                    if (tagText.any { it == w }) n += 6 else if (tagText.any { contains(it, w) }) n += 3
                    if (contains(t, w)) n += 2
                    if (about.isNotEmpty() && contains(about, w)) n += 1
                }
                if (n > 0) score[genre] = n
            }
            // "Fiction", "novel" and the like say little: a book tagged with them and anything else is the anything else.
            val best = score.entries.sortedByDescending { it.value }
            val top = best.firstOrNull() ?: return null
            if (top.key == LITERARY && best.size > 1 && best[1].value >= top.value / 2) return best[1].key
            return top.key
        }

        fun guess(tags: List<String>, series: String?, title: String?, description: String?): CoverGenre =
            guessOrNull(tags, series, title, description) ?: LITERARY
    }
}
