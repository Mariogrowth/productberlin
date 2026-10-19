package net.productberlin.domain.entity

/** A curated organiser's public calendar (`cloudflare/event-sources.json`). */
data class EventSource(
    val id: String,
    val name: String,
    /** The kind of most of its events; a title can still mark one as a startup event. */
    val kind: EventKind,
    /** Berlin organisers' events count unless they name another place; others' need Berlin evidence. */
    val berlinBased: Boolean,
)
