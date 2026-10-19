package net.productberlin.domain.entity

/** An upcoming in-person event in Berlin from a curated organiser's public calendar. */
data class StartupEvent(
    val id: String,
    val title: String,
    /** ISO-8601 UTC. */
    val startsAt: String,
    val endsAt: String?,
    val organiser: String,
    val kind: EventKind,
    /** Venue as published, e.g. "Merantix AI Campus Berlin"; null when only guests see it. */
    val venue: String? = null,
    /** The organiser's event page, where people register. */
    val url: String? = null,
)
