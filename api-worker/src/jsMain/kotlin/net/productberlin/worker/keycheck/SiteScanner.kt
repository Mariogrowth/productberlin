package net.productberlin.worker.keycheck

import kotlin.coroutines.cancellation.CancellationException
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.usecase.FindExposedKeys

/**
 * Reads what a site sends to every visitor and checks it for credentials: the homepage (inline scripts included),
 * the site's own scripts, and the source maps those scripts point to. Passive only: no hidden paths are probed, and
 * a found key is never used. Third-party scripts are skipped (their keys are not the site's), redirects must stay on
 * the same site over HTTPS, and at most [MAX_FETCHES] files are read, within the Worker's subrequest limit.
 */
internal class SiteScanner(
    private val fetcher: PublicFetcher,
    private val find: FindExposedKeys = FindExposedKeys(),
) {
    suspend fun scan(domain: String): KeyScanResult {
        var fetches = 0

        suspend fun follow(start: String): Pair<String, String>? {
            var url = start
            repeat(MAX_REDIRECTS + 1) {
                if (fetches >= MAX_FETCHES) return null
                fetches++
                // DNS failures, timeouts and refused connections all mean the file is unreachable.
                val page =
                    try {
                        fetcher.get(url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        return null
                    }
                if (page.status in 200..299) return url to page.body
                val next = page.location?.takeIf { page.status in 300..399 }?.let { resolve(it, url) } ?: return null
                if (!sameSite(next, domain)) return null
                url = next
            }
            return null
        }

        val (home, html) = follow("https://$domain/") ?: return KeyScanResult.Unreachable
        val findings = mutableListOf<KeyFinding>()
        findings += find(html, path(home))
        var filesScanned = 1
        var sourceMaps = 0
        val scripts = scriptUrls(html, home).filter { sameSite(it, domain) }.distinct()
        val maps = mutableListOf<String>()
        for (script in scripts) {
            val (url, body) = follow(script) ?: continue
            filesScanned++
            findings += find(body, path(url))
            SOURCE_MAP
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.takeUnless { it.startsWith("data:") }
                ?.let { resolve(it, url) }
                ?.takeIf { sameSite(it, domain) }
                ?.let { maps += it }
        }
        for (map in maps.distinct()) {
            val (url, body) = follow(map) ?: continue
            filesScanned++
            sourceMaps++
            findings += find(body, path(url))
        }
        val distinct = findings.distinctBy { it.type to it.masked }
        return KeyScanResult.Completed(KeyScan(domain, filesScanned, sourceMaps, distinct))
    }

    private fun scriptUrls(
        html: String,
        base: String,
    ): List<String> =
        (SCRIPT_SRC.findAll(html).map { it.groupValues[1] } + PRELOAD.findAll(html).map { it.groupValues[1] })
            .mapNotNull { resolve(it, base) }
            .toList()

    private fun resolve(
        href: String,
        base: String,
    ): String? =
        try {
            JsUrl(href.trim(), base).takeIf { it.protocol == "https:" }?.href
        } catch (invalid: Throwable) {
            null
        }

    /** The site itself, its subdomains, or its parent (`www.myapp.com` → `myapp.com`). Never another site. */
    private fun sameSite(
        url: String,
        domain: String,
    ): Boolean {
        val host =
            try {
                JsUrl(url).takeIf { it.protocol == "https:" && it.port.isEmpty() }?.hostname
            } catch (invalid: Throwable) {
                null
            } ?: return false
        return host == domain || host.endsWith(".$domain") || domain.endsWith(".$host")
    }

    private fun path(url: String): String =
        try {
            JsUrl(url).pathname
        } catch (invalid: Throwable) {
            url
        }

    private companion object {
        const val MAX_FETCHES = 40
        const val MAX_REDIRECTS = 3
        val SCRIPT_SRC = Regex("""<script\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val PRELOAD =
            Regex("""<link\b[^>]*\brel\s*=\s*["']?modulepreload["']?[^>]*\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val SOURCE_MAP = Regex("""//[#@]\s*sourceMappingURL=(\S+)\s*$""", RegexOption.MULTILINE)
    }
}

@JsName("URL")
private external class JsUrl(
    url: String,
    base: String = definedExternally,
) {
    val href: String
    val protocol: String
    val hostname: String
    val port: String
    val pathname: String
}
