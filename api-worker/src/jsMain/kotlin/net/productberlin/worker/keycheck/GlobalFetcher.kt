package net.productberlin.worker.keycheck

import kotlin.js.Promise
import kotlinx.coroutines.await

/** The Worker's `fetch`, identified honestly, with a timeout and a size cap per file. */
internal class GlobalFetcher : PublicFetcher {
    override suspend fun get(url: String): FetchedPage {
        val init = js("({})")
        init.redirect = "manual"
        init.headers = js("({})")
        init.headers["User-Agent"] = "ProductBerlinKeyCheck/0.1 (+https://product.berlin/key-check)"
        init.signal = js("AbortSignal.timeout(10000)")
        val fetch = js("globalThis.fetch")
        val response = fetch(url, init).unsafeCast<Promise<dynamic>>().await()
        val status = (response.status as Number).toInt()
        val location = response.headers.get("location") as String?
        if (status !in 200..299) {
            response.body?.cancel()
            return FetchedPage(status, location, "")
        }
        val text = response.text().unsafeCast<Promise<String>>().await()
        return FetchedPage(status, location, text.take(MAX_CHARACTERS))
    }

    private companion object {
        const val MAX_CHARACTERS = 5_000_000
    }
}
