package net.productberlin.presentation.designsystem.layouts.news

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.create
import react.dom.flushSync
import web.cssom.getComputedStyle

class NewsFeedTest : DesignSystemTest() {
    @Test
    fun separatesStoriesWithoutADividerAboveTheFirstPublisher() {
        themed()
        render(
            NewsFeed.create {
                stories =
                    listOf(
                        NewsStory("one", "First headline", "Publisher", "2026-09-25", null),
                        NewsStory("two", "Second headline", "Publisher", "2026-09-24", null),
                    )
            },
        )
        val first = getComputedStyle(assertNotNull(container.querySelector(".news-story")))
        val second = getComputedStyle(assertNotNull(container.querySelector(".news-story + .news-story")))
        assertEquals("0px", first.borderTopWidth)
        assertEquals("0px", first.paddingTop)
        assertEquals("1px", second.borderTopWidth)
    }

    @Test
    fun displaysRealLinksAndPublisherWithoutDemoLabelAndCallsCollapse() {
        themed()
        var collapsed = false
        render(
            NewsFeed.create {
                stories =
                    listOf(NewsStory("one", "Company & partners", "Publisher", "2026-09-25", "https://news.google.com/rss/articles/one"))
                isMock = false
                onCollapse = { collapsed = true }
            },
        )
        assertEquals("https://news.google.com/rss/articles/one", container.querySelector("a")?.getAttribute("href"))
        assertEquals("Company & partners", container.querySelector("h4")?.textContent)
        assertTrue(container.textContent.orEmpty().contains("Publisher"))
        assertFalse(container.textContent.orEmpty().contains("Sample story"))
        flushSync { button().click() }
        assertTrue(collapsed)
    }

    @Test
    fun rendersUnsafeLinksAsTextAndExplainsEmptyFeed() {
        render(
            NewsFeed.create {
                stories =
                    listOf(
                        NewsStory("one", "Unsafe link", "Publisher", "2026-09-25", "javascript:alert(1)"),
                    )
                isMock = true
            },
        )
        assertEquals(null, container.querySelector("a"))
        assertTrue(container.textContent.orEmpty().contains("Sample story"))
        render(NewsFeed.create { stories = emptyList() })
        assertTrue(container.textContent.orEmpty().contains("No recent stories available."))
    }
}
