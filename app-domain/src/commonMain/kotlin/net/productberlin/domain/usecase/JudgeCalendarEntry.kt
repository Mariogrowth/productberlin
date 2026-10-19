package net.productberlin.domain.usecase

import net.productberlin.domain.entity.CalendarEntry
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.EventSource
import net.productberlin.domain.entity.StartupEvent

/**
 * Decides whether a calendar entry is an in-person Berlin event, and of which kind.
 * - Online sessions (title says online, virtual, webinar, livestream or Discord) are left out.
 * - Berlin organisers' entries count unless the address clearly names another place.
 * - Other organisers' entries (international companies with events everywhere) count only with Berlin evidence in
 *   the address, the title or the event link; their guest-only addresses are hidden in the feeds, so some Berlin
 *   events of theirs are missed rather than showing events from other cities.
 * - The kind is the organiser's, unless the title marks a startup event (pitch, demo day, investors, founders…).
 */
class JudgeCalendarEntry {
    operator fun invoke(
        entry: CalendarEntry,
        source: EventSource,
    ): StartupEvent? {
        val title = entry.title.lowercase()
        if (ONLINE.containsMatchIn(title)) return null
        val address = entry.location.takeUnless { it.startsWith("http") }.orEmpty()
        val inBerlin =
            if (source.berlinBased) {
                address.isBlank() || !address.contains(',') || BERLIN.containsMatchIn(address)
            } else {
                listOf(address, entry.title, entry.url.orEmpty()).any { BERLIN.containsMatchIn(it) }
            }
        if (!inBerlin) return null
        val kind = if (STARTUP.containsMatchIn(title)) EventKind.Startup else source.kind
        return StartupEvent(
            id = "${source.id}:${entry.uid}",
            title = entry.title,
            startsAt = entry.startsAt,
            endsAt = entry.endsAt,
            organiser = source.name,
            kind = kind,
            venue = address.substringBefore(',').trim().takeIf { it.isNotEmpty() },
            url = entry.url,
        )
    }

    private companion object {
        val ONLINE = Regex("""\b(online|virtual|webinar|livestream|live stream|discord|zoom)\b""")
        val BERLIN = Regex("berlin", RegexOption.IGNORE_CASE)
        val STARTUP =
            Regex(
                """\b(pitch|pitches|demo day|investors?|angels?|founders?|vc|venture|startups?|start-ups?|accelerator|""" +
                    """fundrais\w*|gründer\w*|hackathon|hack)\b""",
            )
    }
}
