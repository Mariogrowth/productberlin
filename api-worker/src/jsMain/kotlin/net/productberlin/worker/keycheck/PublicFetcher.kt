package net.productberlin.worker.keycheck

/** One HTTPS response, redirects not followed. The body is read only for successful responses. */
internal data class FetchedPage(
    val status: Int,
    val location: String?,
    val body: String,
)

internal fun interface PublicFetcher {
    suspend fun get(url: String): FetchedPage
}
