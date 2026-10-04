package net.productberlin.worker.collection

/**
 * Which news publishers may count and be shown. Video platforms are excluded. Publishers on a country-code domain
 * outside the EU, Canada, the US, the UK and Australia are excluded; generic domains (.com, .org, .news, …) are
 * allowed, as are country codes commonly used generically (.io, .ai, .co, .me, .tv, .fm, .gg).
 */
internal object PublisherPolicy {
    private val EU =
        setOf(
            "at",
            "be",
            "bg",
            "cy",
            "cz",
            "de",
            "dk",
            "ee",
            "es",
            "fi",
            "fr",
            "gr",
            "hr",
            "hu",
            "ie",
            "it",
            "lt",
            "lu",
            "lv",
            "mt",
            "nl",
            "pl",
            "pt",
            "ro",
            "se",
            "si",
            "sk",
            "eu",
        )
    private val OTHER_ALLOWED = setOf("ca", "us", "uk", "au")
    private val USED_GENERICALLY = setOf("io", "ai", "co", "me", "tv", "fm", "gg")
    private val VIDEO_HOSTS = setOf("youtube.com", "youtu.be")

    fun allows(
        source: String,
        sourceUrl: String?,
    ): Boolean {
        if (source.trim().equals("YouTube", ignoreCase = true)) return false
        val host = sourceUrl?.let(::host) ?: return true
        if (VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }) return false
        val tld = host.substringAfterLast('.')
        return tld.length != 2 || tld in EU || tld in OTHER_ALLOWED || tld in USED_GENERICALLY
    }

    private fun host(url: String): String? =
        Regex("^https?://([^/:?#]+)", RegexOption.IGNORE_CASE)
            .find(url.trim())
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?.trimEnd('.')
}
