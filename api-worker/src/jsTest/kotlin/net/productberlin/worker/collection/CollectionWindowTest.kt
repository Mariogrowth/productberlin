package net.productberlin.worker.collection

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.productberlin.domain.entity.StartupCandidate

class CollectionWindowTest {
    @Test
    fun windowsStaySevenUtcDaysAcrossYearLeapDayAndDaylightSaving() {
        for ((time, start, end) in listOf(
            Triple("2026-01-02T06:00:00Z", "2025-12-26", "2026-01-02"),
            Triple("2024-03-04T06:00:00Z", "2024-02-26", "2024-03-04"),
            Triple("2026-03-30T06:00:00+02:00", "2026-03-23", "2026-03-30"),
            Triple("2026-10-26T06:00:00+01:00", "2026-10-19", "2026-10-26"),
        )) {
            val window = CollectionWindow.endingAt(Date.parse(time))
            assertEquals(start + "T00:00:00.000Z", window.start)
            assertEquals(end + "T00:00:00.000Z", window.end)
            assertEquals(7 * CollectionWindow.DAY, window.endMillis - window.startMillis)
            assertEquals(14, window.discoverySearches.distinct().size)
            for ((day, searches) in window.discoverySearches.chunked(2).withIndex()) {
                val from = Date(window.startMillis + day * CollectionWindow.DAY).toISOString().take(10)
                val to = Date(window.startMillis + (day + 1) * CollectionWindow.DAY).toISOString().take(10)
                assertEquals(NewsLanguage.entries.toSet(), searches.map { it.language }.toSet())
                assertTrue(searches.all { it.query.endsWith("after:$from before:$to") })
            }
        }
    }

    @Test
    fun rejectsNonFiniteScheduledTimes() {
        for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { CollectionWindow.endingAt(time) }
        }
    }

    @Test
    fun followupsQuoteCompanyNamesAndIgnoreBlankContext() {
        val window = CollectionWindow.endingAt(Date.parse("2026-09-28T06:00:00Z"))
        val company = StartupCandidate("one", "One \"AI\"", "Description", "Tech", contextKeywords = listOf("", "AI tools"))
        assertEquals("\"One AI\" (\"AI tools\") after:2026-09-21 before:2026-09-28", window.companySearches(company).first().query)
        assertEquals(
            "\"One AI\" after:2026-09-21 before:2026-09-28",
            window.companySearches(company.copy(contextKeywords = emptyList())).first().query,
        )
    }
}
