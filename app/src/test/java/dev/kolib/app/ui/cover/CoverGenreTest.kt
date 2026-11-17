package dev.kolib.app.ui.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGenreTest {

    private fun guess(vararg tags: String, series: String? = null, title: String? = null, about: String? = null) =
        CoverGenre.guessOrNull(tags.toList(), series, title, about)

    @Test
    fun knownSeriesAndFandomsSayWhatTheyAre() {
        assertEquals(CoverGenre.STORM_EPIC, guess(series = "The Stormlight Archive", title = "The Way of Kings"))
        assertEquals(CoverGenre.FANTASY, guess(series = "The Wheel of Time"))
        assertEquals(CoverGenre.SPACE_OPERA, guess(series = "Star Wars: Thrawn"))
        assertEquals(CoverGenre.STARSHIP, guess("Star Trek: The Next Generation", "Jean-Luc Picard"))
        // As an archive of fan fiction tags them.
        assertEquals(CoverGenre.MAGIC_SCHOOL, guess("Harry Potter - J. K. Rowling", "Hermione Granger", "Fluff"))
        assertEquals(CoverGenre.DETECTIVE, guess("Sherlock (TV)", "John Watson", "Angst"))
        assertEquals(CoverGenre.SUPERHERO, guess("Marvel Cinematic Universe", "Found Family"))
        assertEquals(CoverGenre.SPORTS, guess("Haikyuu!!", "Slow Burn"))
        assertEquals(CoverGenre.MUSIC, guess("One Direction (Band)", "Band Fic"))
    }

    @Test
    fun aFandomsDarkerSideWhenTheTagsLeanThatWay() {
        assertEquals(CoverGenre.DARK_MAGIC, guess("Harry Potter - J. K. Rowling", "Slytherin Harry Potter", "Dark Harry Potter"))
        assertEquals(CoverGenre.DARK_MAGIC, guess("Harry Potter - J. K. Rowling", "Death Eaters", "Draco Malfoy/Harry Potter"))
        assertEquals(CoverGenre.DARK_ROMANCE, guess("A Court of Thorns and Roses - Sarah J. Maas", "Possessive Behavior"))
        // A fandom without one stays as it is.
        assertEquals(CoverGenre.SPACE_OPERA, guess("Star Wars - All Media Types", "Dark Side"))
    }

    @Test
    fun aModernSettingLeavesTheFandomsWorldBehind() {
        assertEquals(CoverGenre.MODERN_ROMANCE, guess("Harry Potter - J. K. Rowling", "Alternate Universe - Modern Setting", "Draco Malfoy/Harry Potter", "Slow Burn"))
        assertEquals(CoverGenre.SLICE_OF_LIFE, guess("Harry Potter - J. K. Rowling", "Alternate Universe - Coffee Shops & Cafés"))
        assertEquals(CoverGenre.SCHOOL, guess("Star Wars - All Media Types", "Alternate Universe - College/University", "College AU"))
    }

    @Test
    fun theTagsSayTheMood() {
        assertEquals(CoverGenre.FLUFF, guess("Fluff", "Tooth-Rotting Fluff", "Cuddling"))
        assertEquals(CoverGenre.ANGST, guess("Angst", "Hurt/Comfort", "Major Character Death"))
        assertEquals(CoverGenre.CHRISTMAS, guess("Christmas", "Snowed In"))
        assertEquals(CoverGenre.HALLOWEEN, guess("Halloween", "Spooky"))
        assertEquals(CoverGenre.VAMPIRES, guess("Vampires", "Werewolves"))
        assertEquals(CoverGenre.WUXIA, guess("Xianxia", "Cultivation"))
        assertEquals(CoverGenre.SAMURAI, guess("Samurai", "Feudal Japan"))
        assertEquals(CoverGenre.SAMURAI, guess("Ronin", "Edo period"))
        assertEquals(CoverGenre.CYBERPUNK, guess("Cyberpunk", "Hackers"))
        assertEquals(CoverGenre.WAR, guess("World War II", "Soldiers"))
        assertEquals(CoverGenre.REGENCY, guess("Regency Romance", "Duke"))
        // A pairing makes a love story of an otherwise plain one.
        assertEquals(CoverGenre.ROMANCE, guess("Alice/Bob", "Love"))
        // Dark words alone aren't dark romance without the romance.
        assertTrue(guess("Dark", "Horror", "Ghosts") != CoverGenre.DARK_ROMANCE)
    }

    @Test
    fun littleToGoOnIsNothing() {
        assertNull(guess())
        // One word in a long description says too little.
        assertNull(guess(about = "A story about a family."))
        assertEquals(CoverGenre.LITERARY, CoverGenre.guess(emptyList(), null, null, null))
        // A title has to name a series as words of its own.
        assertNull(guess(title = "Dunedin Days"))
    }

    @Test
    fun everyGenreIsInAGroup() {
        assertEquals(CoverGenre.entries.toSet(), CoverGenre.Group.entries.flatMap { it.genres }.toSet())
        assertTrue(CoverGenre.Group.entries.all { it.genres.isNotEmpty() })
    }
}
