package net.productberlin.domain.entity

/** An event as read from an organiser's iCal feed, before the Berlin and kind rules are applied. */
data class CalendarEntry(
    val uid: String,
    val title: String,
    val startsAt: String,
    val endsAt: String?,
    /** Address or venue text; empty when the feed hides it. */
    val location: String,
    val url: String?,
)
