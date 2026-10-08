package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.productberlin.domain.entity.NewsArticle

class NewsStoryMapperTest {
    private val german =
        NewsArticle("a", "Der Zinsstreit bei Trade Republic geht weiter", "WiWo", "2026-10-01T09:00:00.000Z", "https://x.test/a")

    @Test
    fun translatedHeadlinesAreShownWithTheOriginalKept() {
        val story = german.copy(translatedHeadline = "The interest rate dispute at Trade Republic continues").toNewsStory()
        assertEquals("The interest rate dispute at Trade Republic continues", story.headline)
        assertEquals("Der Zinsstreit bei Trade Republic geht weiter", story.originalHeadline)
        assertEquals("2026-10-01", story.date)
    }

    @Test
    fun untranslatedHeadlinesShowAsTheyAre() {
        val story = german.toNewsStory()
        assertEquals(german.headline, story.headline)
        assertNull(story.originalHeadline)
    }
}
