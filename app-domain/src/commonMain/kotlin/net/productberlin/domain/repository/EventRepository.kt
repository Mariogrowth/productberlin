package net.productberlin.domain.repository

import net.productberlin.domain.entity.StartupEvent

interface EventRepository {
    /** Berlin startup and tech events in the coming week, soonest first. */
    suspend fun getUpcomingEvents(): List<StartupEvent>
}
