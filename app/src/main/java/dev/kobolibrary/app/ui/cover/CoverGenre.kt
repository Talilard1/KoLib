package dev.kobolibrary.app.ui.cover

import dev.kobolibrary.app.tr
import java.util.Locale

/**
 * What sort of book it is, for a cover's picture and the designs that suit it. Worked out from the
 * book's tags (fan fiction's fandoms and tags too), series, title and description ([guess]), and can be
 * changed when making a cover. The genres are in [Group]s, to choose from.
 */
enum class CoverGenre(val label: String, val group: Group) {
    FANTASY(tr("Fantasy"), Group.FANTASY),
    MAGIC_SCHOOL(tr("Magic school"), Group.FANTASY),
    DARK_MAGIC(tr("Dark magic"), Group.FANTASY),
    WITCHES(tr("Witches and spells"), Group.FANTASY),
    QUEST(tr("Quests and legends"), Group.FANTASY),
    STORM_EPIC(tr("Storm-swept epic"), Group.FANTASY),
    MYTHOLOGY(tr("Myths and gods"), Group.FANTASY),
    FAIRY_TALE(tr("Fairy tale"), Group.FANTASY),
    ROMANTASY(tr("Romantic fantasy"), Group.FANTASY),
    URBAN_FANTASY(tr("Urban fantasy"), Group.FANTASY),
    WUXIA(tr("Wuxia and cultivation"), Group.FANTASY),

    SCIENCE_FICTION(tr("Science fiction"), Group.SCIENCE_FICTION),
    SPACE_OPERA(tr("Space opera"), Group.SCIENCE_FICTION),
    STARSHIP(tr("Starship voyages"), Group.SCIENCE_FICTION),
    CYBERPUNK(tr("Cyberpunk"), Group.SCIENCE_FICTION),
    DYSTOPIA(tr("Dystopia and apocalypse"), Group.SCIENCE_FICTION),
    STEAMPUNK(tr("Steampunk"), Group.SCIENCE_FICTION),
    SUPERHERO(tr("Superheroes"), Group.SCIENCE_FICTION),

    MYSTERY(tr("Mystery and crime"), Group.MYSTERY),
    DETECTIVE(tr("Victorian detective"), Group.MYSTERY),
    COSY_MYSTERY(tr("Cosy mystery"), Group.MYSTERY),
    THRILLER(tr("Thriller"), Group.MYSTERY),
    SPY(tr("Spies"), Group.MYSTERY),

    HISTORICAL(tr("Historical"), Group.HISTORY),
    WAR(tr("War"), Group.HISTORY),
    KNIGHTS(tr("Knights and battles"), Group.HISTORY),
    REGENCY(tr("Regency and period drama"), Group.HISTORY),
    ANCIENT(tr("Ancient worlds"), Group.HISTORY),
    ADVENTURE(tr("Adventure"), Group.HISTORY),
    WESTERN(tr("Western"), Group.HISTORY),

    ROMANCE(tr("Romance"), Group.ROMANCE),
    MODERN_ROMANCE(tr("Modern romance"), Group.ROMANCE),
    DARK_ROMANCE(tr("Dark romance"), Group.ROMANCE),
    FLUFF(tr("Fluff and comfort"), Group.ROMANCE),
    ANGST(tr("Angst and heartbreak"), Group.ROMANCE),

    HORROR(tr("Horror"), Group.HORROR),
    VAMPIRES(tr("Vampires and werewolves"), Group.HORROR),
    COSMIC_HORROR(tr("Cosmic horror"), Group.HORROR),
    HALLOWEEN(tr("Halloween"), Group.HORROR),

    LITERARY(tr("Literary and classics"), Group.EVERYDAY),
    SLICE_OF_LIFE(tr("Coffee shops and city life"), Group.EVERYDAY),
    SCHOOL(tr("School and university"), Group.EVERYDAY),
    SPORTS(tr("Sport"), Group.EVERYDAY),
    MUSIC(tr("Music and fame"), Group.EVERYDAY),
    CHRISTMAS(tr("Christmas and winter"), Group.EVERYDAY),
    NON_FICTION(tr("Non-fiction"), Group.EVERYDAY),
    CHILDREN(tr("Children's"), Group.EVERYDAY),
    ;

    /** The genres, gathered by kind, as they're offered to choose from. */
    enum class Group(val label: String) {
        FANTASY(tr("Fantasy and magic")),
        SCIENCE_FICTION(tr("Science fiction")),
        MYSTERY(tr("Mystery and thrillers")),
        HISTORY(tr("History, war and adventure")),
        ROMANCE(tr("Romance")),
        HORROR(tr("Horror and the supernatural")),
        EVERYDAY(tr("Everyday life and more")),
        ;

        val genres: List<CoverGenre> get() = CoverGenre.entries.filter { it.group == this }
    }

    companion object {
        /** Words that say what a book is; the book's tags count for most. */
        private val words: Map<CoverGenre, List<String>> = mapOf(
            FANTASY to listOf(
                "fantasy", "epic fantasy", "high fantasy", "sword and sorcery", "magic", "wizard", "sorcer", "dragon", "dragons", "elves",
                "elf", "dwarves", "orcs", "kingdom", "realm", "throne", "prophecy", "chosen one", "litrpg", "progression fantasy",
                "grimdark", "isekai", "dark fantasy", "dragon rider", "mage", "mages",
            ),
            MAGIC_SCHOOL to listOf(
                "magic school", "school of magic", "wizarding school", "magical school", "magic academy", "academy of magic", "boarding school",
                "wand", "wands", "spellbook", "potions", "quidditch", "house points", "sorting ceremony", "eighth year", "8th year",
            ),
            DARK_MAGIC to listOf(
                "dark magic", "dark arts", "necromancy", "necromancer", "blood magic", "dark lord", "death eater", "death eaters", "slytherin",
                "dark wizard", "curses", "cursed", "forbidden magic", "dark side", "sith", "dark harry", "horcrux", "horcruxes", "demon",
                "demons", "demonic", "occultism", "black magic",
            ),
            WITCHES to listOf("witch", "witches", "witchcraft", "coven", "spells", "hex", "hexes", "cauldron", "familiar", "grimoire", "pagan", "wicca"),
            QUEST to listOf(
                "quest", "quests", "legend", "legends", "arthurian", "king arthur", "camelot", "excalibur", "fellowship", "hobbit", "hobbits",
                "ranger", "rangers", "epic journey",
            ),
            STORM_EPIC to listOf("highstorm", "tempest", "storms", "knights radiant", "shardblade", "spren", "stormlight", "cosmere", "radiants"),
            MYTHOLOGY to listOf(
                "mythology", "myth", "myths", "greek mythology", "norse mythology", "egyptian mythology", "gods", "goddess", "goddesses",
                "olympus", "olympians", "demigod", "demigods", "valhalla", "asgard", "odin", "zeus", "hades", "persephone", "poseidon",
            ),
            FAIRY_TALE to listOf(
                "fairy tale", "fairy tales", "fairytale", "fairytales", "once upon a time", "princess", "retelling", "cinderella",
                "snow white", "sleeping beauty", "rapunzel", "beauty and the beast", "little mermaid", "red riding hood", "folklore", "folk tale",
            ),
            ROMANTASY to listOf("romantasy", "romantic fantasy", "fae", "faerie", "fairy", "fae romance", "high fae", "fated mates", "fae court", "fae courts"),
            URBAN_FANTASY to listOf(
                "urban fantasy", "paranormal", "shapeshifter", "shapeshifters", "angels", "angel", "demons and angels", "shadowhunters",
                "hidden world", "magic in the modern world",
            ),
            WUXIA to listOf(
                "wuxia", "xianxia", "xuanhuan", "cultivation", "cultivator", "cultivators", "jianghu", "martial arts", "kung fu", "samurai",
                "ninja", "ninjas", "shogun", "chinese mythology", "japanese mythology", "danmei", "murim",
            ),
            SCIENCE_FICTION to listOf(
                "science fiction", "sci-fi", "scifi", "sf", "space", "alien", "aliens", "robot", "robots", "android", "androids", "time travel",
                "futuristic", "mars", "artificial intelligence", "first contact", "planet", "planets",
            ),
            SPACE_OPERA to listOf(
                "space opera", "galactic", "galaxy", "galactic empire", "starfighter", "space battle", "military science fiction",
                "military sci-fi", "jedi", "the force", "lightsaber", "clone troopers", "bounty hunter", "smuggler", "space war",
            ),
            STARSHIP to listOf(
                "starship", "starships", "spaceship", "spaceships", "space exploration", "warp", "space station", "interstellar", "deep space",
                "first officer", "away team", "bridge crew",
            ),
            CYBERPUNK to listOf("cyberpunk", "neon", "hacker", "hackers", "cyborg", "cyborgs", "megacorp", "megacorporation", "virtual reality", "netrunner", "augmented"),
            DYSTOPIA to listOf(
                "dystopia", "dystopian", "post-apocalyptic", "post apocalyptic", "apocalypse", "apocalyptic", "zombie", "zombies", "zombie apocalypse",
                "end of the world", "survival", "wasteland", "plague", "pandemic", "totalitarian", "the resistance",
            ),
            STEAMPUNK to listOf("steampunk", "airship", "airships", "clockwork", "gaslamp", "dieselpunk", "alchemy", "alchemist", "automaton", "automata", "inventor"),
            SUPERHERO to listOf(
                "superhero", "superheroes", "super hero", "superpowers", "super powers", "vigilante", "vigilantes", "secret identity",
                "superpowered", "quirk", "quirks", "sidekick", "supervillain",
            ),
            MYSTERY to listOf(
                "mystery", "mysteries", "crime", "murder", "whodunit", "police procedural", "noir", "private investigator", "sleuth",
                "inspector", "detective", "investigation", "murder mystery", "police",
            ),
            DETECTIVE to listOf(
                "victorian", "consulting detective", "baker street", "gaslight", "scotland yard", "edwardian", "deerstalker",
                "holmes", "johnlock",
            ),
            COSY_MYSTERY to listOf("cozy mystery", "cosy mystery", "cozy", "cosy", "village mystery", "amateur sleuth", "small town mystery", "tea shop", "bakery"),
            THRILLER to listOf(
                "thriller", "thrillers", "suspense", "conspiracy", "assassin", "assassins", "legal thriller", "political thriller",
                "psychological thriller", "action", "techno-thriller", "heist", "on the run", "hitman", "mafia", "organized crime", "organised crime",
            ),
            SPY to listOf("spy", "spies", "espionage", "secret agent", "secret agents", "undercover", "cold war", "mi6", "cia", "kgb", "double agent"),
            HISTORICAL to listOf(
                "historical fiction", "historical", "history", "medieval", "tudor", "renaissance", "napoleonic", "civil war", "georgian",
                "18th century", "19th century", "17th century", "period piece", "period drama",
            ),
            WAR to listOf(
                "war", "world war", "world war i", "world war ii", "wwi", "wwii", "ww1", "ww2", "soldier", "soldiers", "military", "army",
                "trenches", "the front", "veteran", "veterans", "war fic", "wartime", "marines", "battlefield",
            ),
            KNIGHTS to listOf("knight", "knights", "siege", "chivalry", "crusade", "crusades", "vikings", "viking", "warrior", "warriors", "sword fighting", "battle"),
            REGENCY to listOf(
                "regency", "regency era", "regency romance", "historical romance", "duke", "duchess", "earl", "viscount", "ballroom",
                "the ton", "courtship", "jane austen", "austen", "victorian romance", "period romance", "debutante",
            ),
            ANCIENT to listOf(
                "ancient", "ancient rome", "ancient greece", "ancient egypt", "roman empire", "pharaoh", "gladiator", "gladiators", "sparta",
                "spartan", "troy", "trojan war", "achilles", "patroclus", "bronze age",
            ),
            ADVENTURE to listOf(
                "adventure", "adventures", "pirate", "pirates", "sea", "ocean", "treasure", "expedition", "explorer", "jungle",
                "nautical", "naval", "sailing", "treasure hunt", "island",
            ),
            WESTERN to listOf("western", "westerns", "cowboy", "cowboys", "frontier", "outlaw", "outlaws", "ranch", "gunslinger", "wild west", "sheriff"),
            ROMANCE to listOf(
                "romance", "romantic", "love story", "love", "slow burn", "enemies to lovers", "friends to lovers", "rivals to lovers",
                "fake dating", "pining", "mutual pining", "first kiss", "soulmates", "soulmate", "soulmate au", "love confession", "marriage",
                "wedding", "kissing", "established relationship", "falling in love", "romantic tension",
            ),
            MODERN_ROMANCE to listOf(
                "contemporary romance", "modern romance", "romcom", "rom-com", "romantic comedy", "modern setting", "modern au",
                "office romance", "dating", "first date", "roommates", "neighbours", "neighbors", "only one bed", "there was only one bed",
            ),
            DARK_ROMANCE to listOf(
                "dark romance", "dark", "obsession", "obsessive", "possessive", "possessive behavior", "possessive behaviour", "yandere", "captivity",
                "kidnapping", "stalking", "toxic relationship", "mafia romance", "dubious consent", "morally grey", "morally gray", "villain romance",
            ),
            FLUFF to listOf(
                "fluff", "tooth-rotting fluff", "pure fluff", "domestic fluff", "fluff and humor", "fluff and humour", "cuddling", "cuddles",
                "comfort", "wholesome", "soft", "domestic", "hugs", "teeth-rotting fluff",
            ),
            ANGST to listOf(
                "angst", "heavy angst", "hurt/comfort", "grief", "mourning", "heartbreak", "tragedy", "major character death",
                "character death", "angst with a happy ending", "sad ending", "unrequited love", "heartbroken",
            ),
            HORROR to listOf(
                "horror", "ghost", "ghosts", "haunted", "haunting", "occult", "supernatural", "gothic", "monster", "monsters", "dark fiction",
                "undead", "creepy", "body horror", "psychological horror", "slasher", "possession", "exorcism",
            ),
            VAMPIRES to listOf("vampire", "vampires", "werewolf", "werewolves", "lycan", "lycans", "fangs", "vampire au", "werewolf au", "shifter", "shifters", "bloodsucker"),
            COSMIC_HORROR to listOf("cosmic horror", "lovecraft", "lovecraftian", "cthulhu", "eldritch", "eldritch abomination", "elder gods", "tentacles"),
            HALLOWEEN to listOf("halloween", "spooky", "spooky season", "pumpkin", "pumpkins", "trick or treat", "samhain", "all hallows", "jack-o'-lantern", "spooktober"),
            LITERARY to listOf(
                "literary fiction", "literature", "classic", "classics", "fiction", "novel", "poetry", "poems", "drama", "plays",
                "short stories", "essays",
            ),
            SLICE_OF_LIFE to listOf(
                "slice of life", "coffee shop", "coffee shop au", "coffee shops & cafés", "coffee shops & cafes", "café", "cafe", "barista",
                "bookshop", "bookstore", "flower shop", "florist", "city life", "everyday life", "domesticity",
            ),
            SCHOOL to listOf(
                "school", "high school", "college", "university", "campus", "student", "students", "college au", "high school au",
                "teacher", "professor", "dorm", "exams", "classmates", "dark academia",
            ),
            SPORTS to listOf(
                "sport", "sports", "football", "soccer", "hockey", "ice hockey", "basketball", "baseball", "volleyball", "tennis", "rugby",
                "figure skating", "swimming", "olympics", "athlete", "athletes", "formula 1", "f1", "racing", "sports au",
            ),
            MUSIC to listOf(
                "music", "musician", "musicians", "band", "bands", "rock star", "rockstar", "pop star", "popstar", "idol", "idols",
                "concert", "on tour", "singer", "singing", "k-pop", "kpop", "fame", "celebrity", "celebrities", "famous", "band au",
            ),
            CHRISTMAS to listOf(
                "christmas", "xmas", "holiday", "holidays", "winter", "snow", "snowed in", "advent", "new year", "new year's eve", "mistletoe",
                "hanukkah", "yule", "winter holidays", "christmas fluff",
            ),
            NON_FICTION to listOf(
                "non-fiction", "nonfiction", "biography", "autobiography", "memoir", "science", "philosophy", "politics", "economics",
                "business", "self-help", "psychology", "religion", "theology", "cookery", "cooking", "travel", "reference", "textbook",
                "language", "grammar", "dictionary", "lexicon", "mathematics", "programming", "health",
            ),
            CHILDREN to listOf(
                "children", "children's", "childrens", "kids", "juvenile", "picture book", "middle grade", "bedtime", "nursery",
            ),
        )

        /**
         * A series or fandom well enough known to say what it is, whatever its books are tagged; with, for
         * some, a darker side ([darker]) that the tags can call for.
         */
        private class Known(val genre: CoverGenre, val darker: CoverGenre? = null)

        private val known: Map<String, Known> = buildMap {
            fun put(genre: CoverGenre, darker: CoverGenre?, vararg names: String) = names.forEach { put(it, Known(genre, darker)) }
            put(MAGIC_SCHOOL, DARK_MAGIC, "harry potter", "hogwarts", "fantastic beasts", "wizarding world", "the magicians", "drarry", "dramione", "snarry")
            put(MYTHOLOGY, null, "percy jackson", "heroes of olympus", "camp half-blood", "trials of apollo", "kane chronicles", "lore olympus", "hadestown", "god of war")
            put(QUEST, DARK_MAGIC, "lord of the rings", "the hobbit", "silmarillion", "middle-earth", "middle earth", "rings of power", "narnia", "earthsea",
                "eragon", "inheritance cycle", "the dark crystal", "redwall")
            put(STORM_EPIC, null, "stormlight", "way of kings", "cosmere", "words of radiance", "oathbringer", "rhythm of war", "wind and truth")
            put(FANTASY, DARK_MAGIC, "wheel of time", "mistborn", "discworld", "witcher", "song of ice and fire", "game of thrones", "house of the dragon",
                "dragonlance", "forgotten realms", "malazan", "shannara", "sword of truth", "first law", "kingkiller", "realm of the elderlings", "riftwar",
                "dragon age", "elder scrolls", "skyrim", "baldur's gate", "dungeons & dragons", "dungeons and dragons", "critical role", "genshin impact",
                "grishaverse", "shadow and bone", "six of crows", "legend of zelda", "final fantasy", "elden ring", "dark souls", "temeraire", "wings of fire",
                "how to train your dragon", "his dark materials", "league of legends", "fire emblem", "the dragon prince")
            put(ROMANTASY, DARK_ROMANCE, "court of thorns and roses", "acotar", "throne of glass", "fourth wing", "the empyrean", "crescent city",
                "the cruel prince", "folk of the air", "from blood and ash")
            put(URBAN_FANTASY, null, "shadowhunters", "mortal instruments", "dresden files", "good omens", "american gods", "rivers of london", "the raven cycle", "lockwood")
            put(WUXIA, DARK_MAGIC, "mo dao zu shi", "grandmaster of demonic cultivation", "the untamed", "heaven official's blessing", "tian guan ci fu",
                "scum villain", "svsss", "2ha", "erha", "word of honor", "naruto", "avatar: the last airbender", "the last airbender", "legend of korra",
                "demon slayer", "kimetsu no yaiba", "sekiro", "ghost of tsushima", "nirvana in fire", "mulan")
            put(FAIRY_TALE, null, "once upon a time (tv)", "grimm's fairy tales", "hans christian andersen", "into the woods", "ever after high", "the princess bride",
                "tangled", "shrek")
            put(DARK_MAGIC, null, "jujutsu kaisen", "hazbin hotel", "helluva boss", "chilling adventures of sabrina", "bleach (anime & manga)", "chainsaw man", "berserk")
            put(WITCHES, null, "practical magic", "charmed", "the owl house", "kiki's delivery service", "agatha all along", "little witch academia", "the worst witch")
            put(SPACE_OPERA, null, "star wars", "the mandalorian", "clone wars", "the bad batch", "andor", "obi-wan kenobi", "skywalker", "the expanse",
                "mass effect", "halo (video games)", "warhammer 40", "red rising", "honor harrington", "battlestar galactica", "gundam", "voltron",
                "guardians of the galaxy", "star citizen", "stellaris", "starcraft", "valerian", "the murderbot diaries", "murderbot")
            put(STARSHIP, null, "star trek", "deep space nine", "next generation", "strange new worlds", "lower decks", "starfleet", "firefly", "serenity (2005)",
                "stargate", "doctor who", "the orville", "farscape", "wayfarers", "long way to a small angry planet", "no man's sky")
            put(SCIENCE_FICTION, null, "dune", "foundation (tv)", "asimov", "the martian", "interstellar (2014)", "the three-body problem", "3 body problem",
                "hitchhiker's guide", "portal (video game)", "subnautica", "outer wilds")
            put(CYBERPUNK, null, "cyberpunk", "blade runner", "ghost in the shell", "detroit: become human", "deus ex", "altered carbon", "akira", "edgerunners", "neuromancer")
            put(DYSTOPIA, null, "hunger games", "divergent", "maze runner", "the last of us", "fallout", "walking dead", "attack on titan", "shingeki no kyojin",
                "the 100", "handmaid's tale", "station eleven", "metro 2033", "resident evil", "death stranding", "snowpiercer", "severance")
            put(STEAMPUNK, null, "arcane (league of legends)", "fullmetal alchemist", "bioshock", "dishonored", "mortal engines", "the parasol protectorate",
                "girl genius", "howl's moving castle", "steamboy")
            put(SUPERHERO, null, "marvel", "avengers", "mcu", "x-men", "spider-man", "spiderman", "batman", "superman", "justice league", "dc comics", "dcu",
                "my hero academia", "boku no hero academia", "the boys", "invincible", "the umbrella academy", "teen titans", "young justice", "one punch man",
                "miraculous ladybug", "worm (web serial)", "venom (marvel)")
            put(DETECTIVE, null, "sherlock", "sherlock holmes", "enola holmes", "the great ace attorney", "moriarty the patriot", "yuukoku no moriarty",
                "penny dreadful", "ripper street", "the alienist")
            put(COSY_MYSTERY, null, "miss marple", "murder she wrote", "midsomer murders", "only murders in the building", "father brown", "the thursday murder club",
                "agatha raisin", "death in paradise", "veronica mars", "scooby-doo")
            put(MYSTERY, null, "poirot", "hercule poirot", "agatha christie", "knives out", "maigret", "inspector lewis", "endeavour", "broadchurch", "twin peaks",
                "detective conan", "case closed", "true detective", "criminal minds", "brooklyn nine-nine", "castle (tv)", "bones (tv)", "lucifer (tv)", "psych (tv)")
            put(THRILLER, null, "jack reacher", "john wick", "jack ryan", "mitch rapp", "breaking bad", "better call saul", "peaky blinders", "the sopranos",
                "money heist", "la casa de papel", "squid game", "mr. robot", "hannibal (tv)", "silence of the lambs", "dexter", "you (tv)", "the godfather")
            put(SPY, null, "james bond", "007", "mission: impossible", "the bourne", "kingsman", "killing eve", "spy x family", "the americans",
                "slow horses", "the man from u.n.c.l.e.", "tinker tailor", "le carré", "black widow", "alias (tv)", "chuck (tv)")
            put(WAR, null, "band of brothers", "the pacific", "masters of the air", "saving private ryan", "1917", "all quiet on the western front", "dunkirk",
                "hornblower", "sharpe", "aubrey-maturin", "master and commander", "call of duty", "battlefield (video games)", "hamilton",
                "blackadder", "m*a*s*h", "hogan's heroes", "wolfenstein")
            put(KNIGHTS, null, "the last kingdom", "vikings (tv)", "kingdom come", "merlin (tv)", "bbc merlin", "a knight's tale", "braveheart",
                "the green knight", "mount & blade", "for honor", "ivanhoe")
            put(REGENCY, null, "pride and prejudice", "jane austen", "bridgerton", "persuasion", "sense and sensibility", "emma (2020)", "north and south",
                "outlander", "little women", "jane eyre", "wuthering heights", "downton abbey", "the gilded age", "poldark", "anne of green gables")
            put(ANCIENT, null, "song of achilles", "the iliad", "the odyssey", "rome (tv)", "spartacus", "gladiator (2000)", "assassin's creed origins",
                "assassin's creed odyssey", "the mummy", "troy (2004)", "the aeneid", "hades (video game)")
            put(ADVENTURE, null, "pirates of the caribbean", "our flag means death", "black sails", "one piece", "indiana jones", "uncharted", "tomb raider",
                "treasure planet", "treasure island", "jules verne", "jumanji", "the goonies")
            put(WESTERN, null, "red dead redemption", "yellowstone", "deadwood", "westworld", "lonesome dove", "the magnificent seven", "tombstone", "brokeback mountain")
            put(VAMPIRES, null, "twilight series", "twilight saga", "the vampire diaries", "the originals", "buffy the vampire slayer", "angel (btvs)", "true blood",
                "interview with the vampire", "vampire chronicles", "teen wolf", "what we do in the shadows", "vampire academy", "castlevania", "dracula",
                "vampire knight", "underworld (movies)", "a discovery of witches", "carmilla", "hellsing", "being human")
            put(HORROR, null, "supernatural (tv)", "stranger things", "the haunting of hill house", "it (movies)", "stephen king", "silent hill",
                "five nights at freddy's", "dead by daylight", "evil dead", "the conjuring", "scream (movies)", "wednesday (tv)", "the addams family",
                "american horror story", "the shining", "alien (movies)")
            put(COSMIC_HORROR, null, "lovecraft", "cthulhu", "bloodborne", "the magnus archives", "magnus archives", "welcome to night vale", "night vale",
                "annihilation", "control (video game)", "dredge", "scp foundation")
            put(HALLOWEEN, null, "nightmare before christmas", "hocus pocus", "halloweentown", "coraline", "over the garden wall", "trick 'r treat", "corpse bride")
            put(SLICE_OF_LIFE, null, "gilmore girls", "friends (tv)", "how i met your mother", "the office", "parks and recreation", "schitt's creek", "heartstopper",
                "ted lasso", "the bear (tv)", "stardew valley", "animal crossing", "abbott elementary", "new girl")
            put(SCHOOL, null, "glee", "high school musical", "sex education", "elite (tv)", "never have i ever", "assassination classroom", "danganronpa",
                "persona 5", "persona 4", "kaguya-sama", "the secret history", "dead poets society", "riverdale", "gossip girl", "the society")
            put(SPORTS, null, "haikyuu", "kuroko no basket", "yuri!!! on ice", "yuri on ice", "check please", "blue lock", "free! - iwatobi", "hockey rpf",
                "formula 1 rpf", "f1 rpf", "football rpf", "soccer rpf", "nhl rpf", "nba rpf", "olympics rpf", "figure skating rpf", "prince of tennis",
                "slam dunk", "eyeshield 21", "ace of diamond", "heated rivalry", "all for the game", "the foxhole court")
            put(MUSIC, null, "one direction", "bts", "bangtan", "stray kids", "seventeen (band)", "ateez", "exo (band)", "5 seconds of summer", "my chemical romance",
                "fall out boy", "panic! at the disco", "twenty one pilots", "the beatles", "queen (band)", "daisy jones", "music rpf", "idolish7",
                "ensemble stars", "love live", "bang dream", "bocchi the rock", "k-pop demon hunters", "the eras tour")
            put(CHRISTMAS, null, "a christmas carol", "the polar express", "elf (2003)", "love actually", "the holiday (2006)", "arthur christmas",
                "frozen (disney movies)", "frozen (2013)", "the grinch", "rise of the guardians")
            put(HISTORICAL, null, "wolf hall", "the tudors", "the crown (tv)", "victoria (tv)", "the great (tv)", "turn: washington's spies", "les misérables",
                "les miserables", "the count of monte cristo", "the three musketeers", "the musketeers", "a tale of two cities", "assassin's creed",
                "the terror (tv)", "1776")
            put(LITERARY, null, "the great gatsby", "the picture of dorian gray", "frankenstein", "moby dick", "the bell jar", "shakespeare", "hamlet",
                "macbeth", "romeo and juliet", "the catcher in the rye")
        }

        /** Words in a tag that say a story leans dark, for a fandom with a darker side. */
        private val darkWords = listOf(
            "dark", "death eater", "death eaters", "slytherin", "villain", "evil", "dark arts", "dark magic", "horcrux", "voldemort", "dark lord",
            "sith", "dark side", "blood magic", "necromancy", "grimdark", "morally grey", "morally gray", "anti-hero", "antihero", "corruption",
            "possessive", "obsession", "demonic",
        )

        /** Tags that move a story out of its fandom's own world, into the everyday one. */
        private val modernSetting = listOf(
            "modern setting", "modern au", "modern day", "no magic", "muggle au", "coffee shop", "coffee shops", "college au", "high school au",
            "university au", "office au", "everyone is human", "all human", "non-magical au", "real world",
        )

        /** The genres a modern, everyday setting rules out. */
        private val otherWorldly = setOf(
            FANTASY, MAGIC_SCHOOL, DARK_MAGIC, WITCHES, QUEST, STORM_EPIC, MYTHOLOGY, FAIRY_TALE, ROMANTASY, WUXIA,
            SCIENCE_FICTION, SPACE_OPERA, STARSHIP, STEAMPUNK, DETECTIVE, HISTORICAL, WAR, KNIGHTS, REGENCY, ANCIENT, WESTERN,
        )

        private fun normal(s: String?) = s.orEmpty().lowercase(Locale.ROOT)

        /** Whether [word] is in [text] as a word (or words) of its own, as "dune" is in "Dune Messiah" but not in "Dunedin". */
        private fun has(text: String, word: String): Boolean {
            var from = 0
            while (true) {
                val at = text.indexOf(word, from)
                if (at < 0) return false
                val before = if (at == 0) ' ' else text[at - 1]
                val after = if (at + word.length >= text.length) ' ' else text[at + word.length]
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
                from = at + 1
            }
        }

        /** The known series or fandom named in [text]: the longest name that's there, so "fullmetal alchemist: brotherhood" beats "fullmetal alchemist". */
        private fun knownIn(text: String): Known? {
            if (text.isEmpty()) return null
            return known.entries.filter { has(text, it.key) }.maxByOrNull { it.key.length }?.value
        }

        /** A pairing on an archive of fan fiction ("A/B"), as against a tag such as "Hurt/Comfort". */
        private fun isPairing(tag: String): Boolean {
            val slash = tag.indexOf('/')
            if (slash <= 0 || slash >= tag.length - 1) return false
            return listOf("comfort", "fantasy", "fiction", "sci", "horror", "drama", "adventure", "humor", "humour").none { tag.contains(it) }
        }

        /** The likeliest genre for a book with these [tags], [series], [title] and [description]; null if nothing says. */
        fun guessOrNull(tags: List<String>, series: String?, title: String?, description: String?): CoverGenre? {
            val tagText = tags.map(::normal)
            val t = normal(title)
            val about = normal(description).take(3000)
            val modern = tagText.any { tag -> modernSetting.any { tag.contains(it) } }

            // A known series or fandom says most: in the series, the tags (fan fiction's fandoms are tags) or the title.
            val fandom = knownIn(normal(series)) ?: tagText.firstNotNullOfOrNull { knownIn(it) } ?: knownIn(t)
            if (fandom != null && !(modern && fandom.genre in otherWorldly)) {
                if (fandom.darker != null && tagText.any { tag -> darkWords.any { has(tag, it) } }) return fandom.darker
                return fandom.genre
            }

            val score = HashMap<CoverGenre, Int>()
            for ((genre, list) in words) {
                var n = 0
                for (w in list) {
                    // A tag that is just the word counts most; a word in a tag, the title or the description less.
                    if (tagText.any { it == w }) n += 6 else if (tagText.any { has(it, w) }) n += 3
                    if (has(t, w)) n += 2
                    if (about.isNotEmpty() && has(about, w)) n += 1
                }
                if (n > 0) score[genre] = n
            }
            if (tagText.any(::isPairing)) score[ROMANCE] = (score[ROMANCE] ?: 0) + 4
            if (modern) {
                // The everyday world: a love story there is a modern one.
                otherWorldly.forEach { score.remove(it) }
                score.remove(ROMANCE)?.let { r -> score[MODERN_ROMANCE] = (score[MODERN_ROMANCE] ?: 0) + r }
                if (score.isEmpty()) return SLICE_OF_LIFE
            }
            val best = score.entries.sortedWith(compareByDescending<Map.Entry<CoverGenre, Int>> { it.value }.thenBy { it.key.ordinal })
            val top = best.firstOrNull() ?: return null
            // One word in the description alone says too little.
            if (top.value < 2) return null
            // "Fiction", "novel" and the like say little: a book tagged with them and anything else is the anything else.
            if (top.key == LITERARY && best.size > 1 && best[1].value >= top.value / 2) return best[1].key
            // A love story with a dark streak is dark romance; dark words alone aren't.
            if (top.key == DARK_ROMANCE && (score[ROMANCE] ?: 0) + (score[MODERN_ROMANCE] ?: 0) == 0) {
                return best.getOrNull(1)?.key ?: HORROR
            }
            return top.key
        }

        fun guess(tags: List<String>, series: String?, title: String?, description: String?): CoverGenre =
            guessOrNull(tags, series, title, description) ?: LITERARY
    }
}
