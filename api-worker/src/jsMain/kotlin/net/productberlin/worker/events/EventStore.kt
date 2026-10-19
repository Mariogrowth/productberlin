package net.productberlin.worker.events

import net.productberlin.domain.entity.StartupEvent

internal interface EventStore {
    /** When events were last collected (ISO-8601), or null. */
    suspend fun lastCollected(): String?

    /** Replaces the upcoming events (from [now]) of every source in [bySource] and records the collection, atomically. */
    suspend fun replace(
        bySource: Map<String, List<StartupEvent>>,
        now: String,
    )
}
