package net.productberlin

import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import react.dom.client.Root
import react.dom.flushSync
import web.dom.document
import web.window.window

class BootstrapTest {
    @Test
    fun mainRouteLoadsTheRankingThroughRealKoinAndKtorWiring() =
        runTest {
            verifyRoute("/", true)
        }

    @Test
    fun designSystemRouteWithTrailingSlashNeverFetchesTheApi() =
        runTest {
            verifyRoute("/design-system/", false)
        }

    private suspend fun verifyRoute(
        path: String,
        loadsApi: Boolean,
    ) {
        val global = js("globalThis")
        val originalFetch = global.fetch
        val originalPath = window.location.href
        val requests = mutableListOf<String>()
        val element = document.createElement("div")
        element.setAttribute("id", "root")
        document.body.appendChild(element)
        var root: Root? = null
        try {
            global.fetch = { input: dynamic, _: dynamic ->
                requests += if (jsTypeOf(input) == "string") input as String else input.url as String
                Promise.resolve<dynamic>(
                    js(
                        "new Response(JSON.stringify({weekLabel:'Bootstrap week',startups:[],isMock:false}),{headers:{'content-type':'application/json'}})",
                    ),
                )
            }
            // History is local to Karma's iframe and restored before the next test.
            global.history.replaceState(null, "", path)
            flushSync { root = mountApplication() }
            withContext(Dispatchers.Default) {
                repeat(100) {
                    if (loadsApi && element.textContent.orEmpty().contains("Bootstrap week")) return@withContext
                    if (!loadsApi && element.textContent.orEmpty().contains("design system", ignoreCase = true)) return@withContext
                    delay(10)
                }
            }
            if (loadsApi) {
                assertEquals(listOf("${window.location.origin}/api/rankings/weekly"), requests)
                assertTrue(element.textContent.orEmpty().contains("Bootstrap week"))
                assertTrue(element.textContent.orEmpty().contains("A quiet week"))
            } else {
                assertTrue(element.textContent.orEmpty().contains("design system", ignoreCase = true))
                assertTrue(requests.isEmpty())
            }
        } finally {
            flushSync { root?.unmount() }
            element.remove()
            global.fetch = originalFetch
            global.history.replaceState(null, "", originalPath)
        }
    }
}
