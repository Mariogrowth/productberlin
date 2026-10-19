package net.productberlin.presentation

import kotlinx.coroutines.CancellationException
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.usecase.GetUpcomingEvents
import react.useEffect
import react.useState

/** The coming week's events, or an empty list while loading or when they cannot be loaded: the section stays hidden. */
internal fun useUpcomingEvents(getEvents: GetUpcomingEvents): List<StartupEvent> {
    var events by useState<List<StartupEvent>>(emptyList())
    useEffect(getEvents) {
        events =
            try {
                getEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
    }
    return events
}
