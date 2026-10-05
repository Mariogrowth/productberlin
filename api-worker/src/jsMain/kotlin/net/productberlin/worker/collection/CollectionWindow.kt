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
    val query: String get() = "Catalogue company names in trusted publishers $dateRange [en,de]"

    private val dateRange: String get() = "after:${start.take(10)} before:${end.take(10)}"

    /**
     * Searches every catalogue company by name (and distinct aliases) over the whole week, in both languages. Google
     * reads only about the first 32 words of a query, so the date range comes first and company names are packed into
     * groups of at most [MAX_GROUP_WORDS] words. Fails rather than silently skipping companies when the catalogue
     * needs more than [MAX_SEARCHES] requests (the Workers free plan allows 50 subrequests per run).
     */
    fun catalogueSearches(catalogue: List<StartupCandidate>): List<NewsSearch> {
        val groups = mutableListOf<MutableList<String>>()
        for (terms in catalogue.map(::companyTerms)) {
            val current = groups.lastOrNull()
            if (current == null || words((current + terms).joinToString(" OR ")) > MAX_GROUP_WORDS) {
                groups += mutableListOf(terms)
            } else {
                current += terms
            }
        }
        val searches =
            groups.flatMap { group ->
                val query = "$dateRange (${group.joinToString(" OR ")})"
                NewsLanguage.entries.map { NewsSearch(query, it) }
            }
        check(searches.size <= MAX_SEARCHES) {
            "Catalogue needs ${searches.size} searches; the per-run budget is $MAX_SEARCHES. Reduce aliases or companies."
        }
        return searches
    }

    private fun companyTerms(company: StartupCandidate): String {
        // An alias that already contains the name (as the matcher normalises it) would add nothing to the search.
        val name = normalised(company.name)
        val aliases = company.aliases.filter { it.isNotBlank() && !normalised(it).contains(name) }
        return (listOf(company.name) + aliases).joinToString(" OR ") { quoted(it) }
    }

    private fun words(value: String): Int = value.split(' ').count { it.isNotBlank() }

    private fun normalised(value: String): String =
        " " +
            value
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString(
                    "",
                ).split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") +
            " "

    private fun quoted(value: String): String = "\"${value.replace("\"", "")}\""

    companion object {
        const val POLICY_VERSION = "trusted-v1"
        const val DAY = 86_400_000.0

        /** Google ignores query words beyond about 32; two are taken by the date range. */
        const val MAX_GROUP_WORDS = 28

        /** Within the Workers free plan's 50 subrequests per run, with room to spare. */
        const val MAX_SEARCHES = 48

        /**
         * The hourly trigger retries failed weeks all week, but a new week's first attempt waits until Monday 06:00 UTC,
         * leaving time for late Sunday coverage to be indexed.
         */
        fun firstAttemptDue(scheduledTime: Double): Boolean {
            val time = Date(scheduledTime)
            return time.getUTCDay() != 1 || time.getUTCHours() >= FIRST_ATTEMPT_HOUR
        }

        const val FIRST_ATTEMPT_HOUR = 6

        /**
         * The Monday–Sunday UTC week that ended at the most recent Monday 00:00 UTC. Any event during a week, whether
         * the first Monday attempt, an hourly retry or a manual trigger, resolves to the same window and collection key,
         * so a published edition stays unchanged until the next Monday.
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
