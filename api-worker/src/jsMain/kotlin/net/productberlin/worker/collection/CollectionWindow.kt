package net.productberlin.worker.collection

import kotlin.js.Date
import net.productberlin.domain.entity.StartupCandidate

internal data class CollectionWindow(
    val start: String,
    val end: String,
) {
    val collectionKey: String get() = "${end.take(10)}-$POLICY_VERSION"
    val startMillis: Double get() = Date.parse(start)
    val endMillis: Double get() = Date.parse(end)
    val label: String get() = "${start.take(10)} – ${Date(endMillis - 1).toISOString().take(10)}"
    val query: String get() = "$DISCOVERY_QUERY after:${start.take(10)} before:${end.take(10)} [en,de]"
    val discoverySearches: List<NewsSearch> get() =
        (0 until 7).flatMap { day ->
            val from = Date(startMillis + day * DAY).toISOString().take(10)
            val to = Date(startMillis + (day + 1) * DAY).toISOString().take(10)
            NewsLanguage.entries.map { language ->
                NewsSearch("$DISCOVERY_QUERY after:$from before:$to", language)
            }
        }

    fun companySearches(company: StartupCandidate): List<NewsSearch> = NewsLanguage.entries.map { NewsSearch(companyQuery(company), it) }

    private fun companyQuery(company: StartupCandidate): String {
        val keywords = company.contextKeywords.filter { it.isNotBlank() }
        val context = if (keywords.isEmpty()) "" else keywords.joinToString(" OR ", " (", ")") { quoted(it) }
        return "${quoted(company.name)}$context after:${start.take(10)} before:${end.take(10)}"
    }

    private fun quoted(value: String): String = "\"${value.replace("\"", "")}\""

    companion object {
        const val POLICY_VERSION = "bilingual-v2"
        const val DISCOVERY_QUERY =
            "(Berlin OR Berliner) (startup OR startups OR \"start-up\" OR \"start-ups\" OR " +
                "funding OR Finanzierung OR Finanzierungsrunde OR Gründer)"
        const val DAY = 86_400_000.0

        /**
         * The Monday–Sunday UTC week that ended at the most recent Monday 00:00 UTC. Any event during a week, whether
         * the Monday cron, a retry or a manual trigger, resolves to the same window and collection key, so a published
         * edition stays unchanged until the next Monday.
         */
        fun latestCompleteWeek(scheduledTime: Double): CollectionWindow {
            require(scheduledTime.isFinite())
            val midnight = Date(Date(scheduledTime).toISOString().take(10) + "T00:00:00.000Z")
            val daysSinceMonday = (midnight.getUTCDay() + 6) % 7
            val end = midnight.getTime() - daysSinceMonday * DAY
            return CollectionWindow(Date(end - 7 * DAY).toISOString(), Date(end).toISOString())
        }
    }
}
