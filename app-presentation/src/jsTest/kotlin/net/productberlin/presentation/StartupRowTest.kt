package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.Startup
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.cssom.ClassName
import web.dom.document
import web.events.Event
import web.events.EventType
import web.html.HTMLButtonElement

/** Existing behavior baseline; Figma visual parity is verified separately once the design is accessible. */
class StartupRowTest : ComponentTest() {
    private val company =
        Startup(
            id = "almedia",
            name = "Almedia",
            description = "Company description",
            category = "Adtech",
            movement = 2,
            reason = "Ranking reason",
            news = listOf(NewsArticle("story-1", "Company launches a product", "Publisher", "Sep 11, 2026")),
        )

    @Test
    fun rendersSuppliedCompanyRankAndNews() {
        render(
            StartupRow.create {
                company = this@StartupRowTest.company
                position = 3
            },
        )

        assertEquals("3.", container.querySelector(".rank")?.textContent)
        assertEquals(company.name, container.querySelector("h3")?.textContent)
        assertEquals(company.description, container.querySelector(".company-description")?.textContent)
        assertEquals(company.category, container.querySelector(".category")?.textContent)
        assertTrue(!container.textContent.orEmpty().contains(company.reason), "Ranking reason is not shown")
        assertEquals(company.news.single().headline, container.querySelector(".news-story h4")?.textContent)
    }

    @Test
    fun disclosureExposesAnAccessibleNameControlsAndToggleState() {
        render(
            StartupRow.create {
                company = this@StartupRowTest.company
                position = 3
            },
        )
        val button = assertNotNull(container.querySelector("button")).unsafeCast<HTMLButtonElement>()
        val content = assertNotNull(container.querySelector("#reason-almedia"))

        assertEquals("Why Almedia is ranked 3", button.getAttribute("aria-label"))
        assertEquals("reason-almedia", button.getAttribute("aria-controls"))
        assertEquals("button", button.getAttribute("type"))
        assertEquals("false", button.getAttribute("aria-expanded"))
        assertTrue(content.classList.contains(ClassName("is-collapsed")))

        flushSync { button.click() }
        assertEquals("true", button.getAttribute("aria-expanded"))
        assertTrue(!content.classList.contains(ClassName("is-collapsed")))

        flushSync { button.click() }
        assertEquals("false", button.getAttribute("aria-expanded"))
        assertTrue(content.classList.contains(ClassName("is-collapsed")))
    }

    @Test
    fun showLessClosesNewsAndReturnsFocusToWhy() {
        render(
            StartupRow.create {
                company = this@StartupRowTest.company
                position = 1
            },
        )
        val why = container.querySelector(".why-button")!!.unsafeCast<HTMLButtonElement>()
        flushSync { why.click() }
        val less = container.querySelector(".pb-news-feed button")!!.unsafeCast<HTMLButtonElement>()
        flushSync { less.click() }
        assertEquals("false", why.getAttribute("aria-expanded"))
        assertEquals(why, document.activeElement)
    }

    @Test
    fun movementHasDescriptiveLabelsForEveryRankingDirection() {
        listOf(2 to "Up 2 places", -2 to "Down 2 places", 0 to "No change this week", null to "New this week")
            .forEach { (movement, label) ->
                render(
                    StartupRow.create {
                        company = this@StartupRowTest.company.copy(movement = movement)
                        position = 1
                    },
                )
                assertEquals(label, container.querySelector(".movement")?.getAttribute("aria-label"))
            }
    }

    @Test
    fun suppliedLogoReplacesTheLetterMarkAsADecorativeImage() {
        render(
            StartupRow.create {
                company = this@StartupRowTest.company.copy(logoUrl = LOGO)
                position = 1
            },
        )
        val mark = assertNotNull(container.querySelector(".company-mark"))
        val image = assertNotNull(mark.querySelector("img"))
        assertEquals(LOGO, image.getAttribute("src"))
        assertEquals("", image.getAttribute("alt"))
        assertEquals("strict-origin-when-cross-origin", image.getAttribute("referrerpolicy"))
        assertEquals("true", mark.getAttribute("aria-hidden"))
        assertEquals("", mark.textContent)
    }

    @Test
    fun failedLogoFallsBackToTheLetterMark() {
        render(
            StartupRow.create {
                company = this@StartupRowTest.company.copy(logoUrl = LOGO)
                position = 1
            },
        )
        val image = assertNotNull(container.querySelector(".company-mark img"))
        flushSync { image.dispatchEvent(Event(EventType("error"))) }
        assertNull(container.querySelector(".company-mark img"))
        assertEquals("a", container.querySelector(".company-mark.mark-almedia")?.textContent)
    }

    @Test
    fun missingOrUnsafeLogoKeepsTheLetterMark() {
        for (logo in listOf(null, "http://cdn.example.com/logo.webp", "javascript:alert(1)", "//cdn.example.com/logo.webp")) {
            render(
                StartupRow.create {
                    company = this@StartupRowTest.company.copy(logoUrl = logo)
                    position = 1
                },
            )
            assertNull(container.querySelector(".company-mark img"))
            assertEquals("a", container.querySelector(".company-mark")?.textContent)
        }
    }

    private companion object {
        const val LOGO = "https://cdn.brandfetch.io/domain/almedia.co/w/80/h/80/fallback/404/type/icon?c=id"
    }
}
