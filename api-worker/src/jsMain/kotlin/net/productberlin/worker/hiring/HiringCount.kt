package net.productberlin.worker.hiring

/** Open roles on one company's job board in one week. */
internal data class HiringCount(
    val startupId: String,
    val provider: JobProvider,
    val totalJobs: Int,
    val berlinJobs: Int,
)
