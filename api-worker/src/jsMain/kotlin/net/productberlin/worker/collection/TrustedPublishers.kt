package net.productberlin.worker.collection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The editorial list of publishers whose articles may count and be shown (`cloudflare/publishers.json`). A source
 * matches its listed domain or any subdomain of it. Sources without a readable site are not trusted.
 */
internal class TrustedPublishers(
    domains: Collection<String>,
) {
    private val domains = domains.toSet()

    fun allows(
        @Suppress("UNUSED_PARAMETER") source: String,
        sourceUrl: String?,
    ): Boolean {
        val host = sourceUrl?.let(::host) ?: return false
        return domains.any { host == it || host.endsWith(".$it") }
    }

    private fun host(url: String): String? =
        Regex("^https?://([^/:?#]+)", RegexOption.IGNORE_CASE)
            .find(url.trim())
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?.trimEnd('.')
}

internal fun parsePublishers(json: String): TrustedPublishers {
    val entries =
        Json.parseToJsonElement(json).jsonArray.map { item ->
            val value = item.jsonObject

            fun field(name: String): String =
                requireNotNull(value[name]).jsonPrimitive.let {
                    require(it.isString && it.content.isNotBlank()) { "Publisher fields must be nonblank strings" }
                    it.content
                }
            Triple(field("domain"), field("name"), field("type"))
        }
    require(entries.size in 1..500) { "Publisher list must have 1–500 entries" }
    require(entries.all { BARE_HOSTNAME.matches(it.first) }) { "Invalid publisher domain" }
    require(entries.map { it.first }.distinct().size == entries.size) { "Duplicate publisher domain" }
    require(entries.all { it.third in TYPES }) { "Unknown publisher type" }
    return TrustedPublishers(entries.map { it.first })
}

private val TYPES = setOf("startup", "trade", "tech", "business")
