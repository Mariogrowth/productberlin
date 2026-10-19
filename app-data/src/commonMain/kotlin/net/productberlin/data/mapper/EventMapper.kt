package net.productberlin.data.mapper

import net.productberlin.contract.EventsDto
import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent

/** Events of a kind this client does not know (from a newer Worker) are skipped. */
fun EventsDto.toDomain(): List<StartupEvent> =
    events.mapNotNull { event ->
        val kind = EventKind.entries.firstOrNull { it.name == event.kind } ?: return@mapNotNull null
        StartupEvent(event.id, event.title, event.startsAt, event.endsAt, event.organiser, kind, event.venue, event.url)
    }
