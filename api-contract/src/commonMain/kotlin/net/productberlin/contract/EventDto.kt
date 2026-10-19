package net.productberlin.contract

import kotlinx.serialization.Serializable

/** One entry of `GET /api/events`. */
@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val startsAt: String,
    val endsAt: String? = null,
    val organiser: String,
    val kind: String,
    val venue: String? = null,
    val url: String? = null,
)
