package net.productberlin.contract

import kotlinx.serialization.Serializable

/** `GET /api/events`: in-person Berlin startup and tech events in the coming week. */
@Serializable
data class EventsDto(
    val events: List<EventDto>,
)
