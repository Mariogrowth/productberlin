package net.productberlin.domain.usecase

import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.repository.EventRepository

class GetUpcomingEvents(
    private val repository: EventRepository,
) {
    suspend operator fun invoke(): List<StartupEvent> = repository.getUpcomingEvents()
}
