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
            Triple("2026-01-05T06:00:00Z", "2025-12-29", "2026-01-05"),
            Triple("2024-03-04T06:00:00Z", "2024-02-26", "2024-03-04"),
            Triple("2026-03-30T06:00:00+02:00", "2026-03-23", "2026-03-30"),
            Triple("2026-10-26T06:00:00+01:00", "2026-10-19", "2026-10-26"),
        )) {
            val window = CollectionWindow.latestCompleteWeek(Date.parse(time))
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
    fun everyEventDuringAWeekResolvesToTheSameCompletedMondayToSundayWindow() {
        val monday = CollectionWindow.latestCompleteWeek(Date.parse("2026-09-28T06:00:00Z"))
        assertEquals("2026-09-21T00:00:00.000Z", monday.start)
        assertEquals("2026-09-28T00:00:00.000Z", monday.end)
        for (time in listOf(
            "2026-09-28T00:00:00.000Z",
            "2026-09-29T12:00:00Z",
            "2026-10-01T02:58:00.413Z",
            "2026-10-03T06:00:00Z",
            "2026-10-04T23:59:59.999Z",
        )) {
            val window = CollectionWindow.latestCompleteWeek(Date.parse(time))
            assertEquals(monday, window, time)
            assertEquals("2026-09-28-${CollectionWindow.POLICY_VERSION}", window.collectionKey)
            assertEquals("2026-09-21 – 2026-09-27", window.label)
        }
        assertEquals("2026-10-05T00:00:00.000Z", CollectionWindow.latestCompleteWeek(Date.parse("2026-10-05T00:00:00.000Z")).end)
    }

    @Test
    fun rejectsNonFiniteScheduledTimes() {
        for (time in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { CollectionWindow.latestCompleteWeek(time) }
        }
    }

    @Test
    fun followupsQuoteCompanyNamesAndIgnoreBlankContext() {
        val window = CollectionWindow.latestCompleteWeek(Date.parse("2026-09-28T06:00:00Z"))
        val company = StartupCandidate("one", "One \"AI\"", "Description", "Tech", contextKeywords = listOf("", "AI tools"))
        assertEquals("\"One AI\" (\"AI tools\") after:2026-09-21 before:2026-09-28", window.companySearches(company).first().query)
        assertEquals(
            "\"One AI\" after:2026-09-21 before:2026-09-28",
            window.companySearches(company.copy(contextKeywords = emptyList())).first().query,
        )
    }
}
