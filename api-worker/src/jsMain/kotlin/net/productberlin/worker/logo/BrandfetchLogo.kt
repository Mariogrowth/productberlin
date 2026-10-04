package net.productberlin.worker.logo

/** A company's logo candidates in display order: the square icon, then the full logo. Either may be absent. */
internal data class CompanyLogos(
    val icon: String? = null,
    val fullLogo: String? = null,
)

/**
 * Builds hotlinked Brandfetch Logo API URLs. Brandfetch requires browsers to load logos directly, so the Worker
 * never requests them; only companies present in a ranking response get URLs. Each URL asks for a 404 when
 * Brandfetch has no such asset, so the UI can try the full logo when there is no square icon, and finally keep its
 * own letter mark.
 */
internal class BrandfetchLogo(
    clientId: String?,
) {
    private val clientId = clientId?.takeIf { CLIENT_ID.matches(it) }

    fun urls(domain: String?): CompanyLogos {
        if (clientId == null || domain == null) return CompanyLogos()
        return CompanyLogos(url(domain, "icon"), url(domain, "logo"))
    }

    private fun url(
        domain: String,
        type: String,
    ) = "https://cdn.brandfetch.io/domain/$domain/w/$SIZE/h/$SIZE/fallback/404/type/$type?c=$clientId"

    private companion object {
        /** Twice the rendered mark size, for high-density screens. */
        const val SIZE = 80
        val CLIENT_ID = Regex("[A-Za-z0-9_-]+")
    }
}
