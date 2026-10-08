package net.productberlin.worker.hiring

internal interface HiringRepository {
    suspend fun isRecorded(week: String): Boolean

    /** Stores the week's counts in one transaction. False when the week was already recorded (nothing changes). */
    suspend fun record(
        week: String,
        counts: List<HiringCount>,
        failed: Int,
        recordedAt: String,
    ): Boolean
}
