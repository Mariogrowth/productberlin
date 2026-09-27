package net.productberlin.worker.repository

import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.worker.collection.CollectionWindow

internal interface CollectionRepository {
    suspend fun acquire(
        week: String,
        now: String,
        token: String,
    ): Boolean

    suspend fun previousPositions(beforeWeek: String): Map<String, Int>

    suspend fun publish(
        window: CollectionWindow,
        ranking: WeeklyRanking,
        token: String,
    ): Boolean

    suspend fun fail(
        week: String,
        token: String,
        now: String,
        error: String,
    )
}
