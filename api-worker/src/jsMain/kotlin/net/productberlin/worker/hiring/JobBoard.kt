package net.productberlin.worker.hiring

/** A catalogue company's public job board (`jobBoard` in `cloudflare/startups.json`). */
internal data class JobBoard(
    val startupId: String,
    val provider: JobProvider,
    val handle: String,
)
