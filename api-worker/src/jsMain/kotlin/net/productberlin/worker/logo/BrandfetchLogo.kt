package net.productberlin.worker.logo

/**
 * Builds hotlinked Brandfetch Logo API URLs. Brandfetch requires browsers to load logos directly, so the Worker
 * never requests them; only companies present in a ranking response get a URL. A 404 fallback lets the UI keep
 * its own letter mark when Brandfetch has no icon.
 */
internal class BrandfetchLogo(
    clientId: String?,
) {
    private val clientId = clientId?.takeIf { CLIENT_ID.matches(it) }

    fun url(domain: String?): String? {
        if (clientId == null || domain == null) return null
        return "https://cdn.brandfetch.io/domain/$domain/w/$SIZE/h/$SIZE/fallback/404/type/icon?c=$clientId"
    }

    private companion object {
        /** Twice the rendered mark size, for high-density screens. */
        const val SIZE = 80
        val CLIENT_ID = Regex("[A-Za-z0-9_-]+")
    }
}
