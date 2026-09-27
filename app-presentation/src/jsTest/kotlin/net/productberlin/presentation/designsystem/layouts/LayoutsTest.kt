package net.productberlin.presentation.designsystem.layouts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.layouts.cards.Card
import net.productberlin.presentation.designsystem.layouts.feedback.EmptyState
import net.productberlin.presentation.designsystem.layouts.filters.FilterBar
import net.productberlin.presentation.designsystem.layouts.filters.FilterOption
import net.productberlin.presentation.designsystem.layouts.ranking.RankedResult
import net.productberlin.presentation.designsystem.layouts.ranking.RankedResultRow
import net.productberlin.presentation.designsystem.layouts.tables.DenseTable
import net.productberlin.presentation.designsystem.layouts.tables.TableColumn
import net.productberlin.presentation.designsystem.layouts.tables.TableRow
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.FC
import react.Props
import react.create
import react.dom.flushSync
import react.dom.html.ReactHTML.div
import react.useState
import web.cssom.getComputedStyle

class LayoutsTest : DesignSystemTest() {
    @Test
    fun filtersReportSelectionsAndReflectControlledState() {
        var selected = "all"
        val options = listOf(FilterOption("all", "View all"), FilterOption("search", "Search"))
        val filters =
            FC<Props> {
                var selection by useState("all")
                FilterBar {
                    label = "Categories"
                    this.options = options
                    selectedId = selection
                    onSelect = {
                        selection = it
                        selected = it
                    }
                }
            }
        render(filters.create())
        flushSync { button("button:nth-child(2)").click() }
        assertEquals("search", selected)
        assertEquals("false", button().getAttribute("aria-pressed"))
        assertEquals("true", button("button:nth-child(2)").getAttribute("aria-pressed"))
    }

    @Test
    fun rankedRowsExposeReasonOnlyAfterDisclosureAndIdsAreUniqueAcrossLists() {
        themed()
        val result = RankedResult("almedia", 1, "Almedia", "A company", 2, "Sample evidence")
        render(div.create { repeat(2) { RankedResultRow { this.result = result } } })
        val reason = assertNotNull(container.querySelector(".pb-result-reason"))
        assertEquals("none", getComputedStyle(reason).display)
        val controlsId = button().getAttribute("aria-controls")
        assertEquals(controlsId, reason.getAttribute("id"))
        assertNotEquals(controlsId, container.querySelectorAll("button").item(1)?.getAttribute("aria-controls"))
        flushSync { button().click() }
        assertEquals("true", button().getAttribute("aria-expanded"))
        assertNotEquals("none", getComputedStyle(reason).display)
        assertEquals("Sample evidence", reason.textContent)
        assertEquals("Up 2 places", container.querySelector(".pb-result-movement")?.getAttribute("aria-label"))
    }

    @Test
    fun patternsRenderContentNumericAlignmentAndRecoveryAction() {
        themed()
        render(
            Card.create {
                elevated = true
                +"Card contents"
            },
        )
        assertEquals("Card contents", container.textContent)
        assertNotEquals("none", getComputedStyle(assertNotNull(container.querySelector(".pb-card"))).boxShadow)
        render(
            DenseTable.create {
                label = "Rankings"
                columns = listOf(TableColumn("Company"), TableColumn("Rank", true))
                rows =
                    listOf(TableRow("a", listOf("Almedia", "1")))
            },
        )
        assertEquals("Rankings", container.querySelector("caption")?.textContent)
        assertEquals("right", getComputedStyle(assertNotNull(container.querySelector("td.pb-numeric"))).textAlign)
        assertEquals("52px", getComputedStyle(assertNotNull(container.querySelector("td"))).height)
        var cleared = false
        render(
            EmptyState.create {
                title = "No matches"
                description = "Try another search"
                actionLabel = "Clear filters"
                onAction =
                    { cleared = true }
            },
        )
        assertEquals("Clear filters", button().textContent)
        flushSync { button().click() }
        assertTrue(cleared)
    }
}
