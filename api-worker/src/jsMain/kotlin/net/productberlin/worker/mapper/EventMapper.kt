package net.productberlin.worker.mapper

import net.productberlin.contract.EventDto
import net.productberlin.contract.EventsDto
import net.productberlin.domain.entity.StartupEvent

internal fun List<StartupEvent>.toDto() =
    EventsDto(
        map {
            EventDto(it.id, it.title, it.startsAt, it.endsAt, it.organiser, it.kind.name, it.venue, it.url)
        },
    )
