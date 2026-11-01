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

    @Test
    fun privacyRouteRendersThePolicyWithoutCallingTheApi() =
        runTest {
            verifyRoute("/privacy", false, "Privacy policy")
        }

    @Test
    fun keyCheckRouteRendersTheCheckerWithoutCallingAnyApiUntilSubmitted() =
        runTest {
            verifyRoute("/key-check", false, "Is your site leaking API keys?")
        }

    @Test
    fun confirmationReturnShowsTheVerifiedBannerAndCleansTheAddress() =
        runTest {
            verifyRoute("/?subscribed=1&ref=mail", true, "Email Verified. Expect a weekly roundup") {
                assertEquals("/?ref=mail", window.location.pathname + window.location.search)
            }
        }

    private suspend fun verifyRoute(
        path: String,
        loadsApi: Boolean,
        expectedText: String = if (loadsApi) LOADED_RANKING else "design system",
        afterMount: () -> Unit = {},
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
                        "new Response(JSON.stringify({weekLabel:'Bootstrap week',startups:[],isMock:false,updatedAt:'2026-09-28T06:00:00Z',articleCount:7}),{headers:{'content-type':'application/json'}})",
                    ),
                )
            }
            // History is local to Karma's iframe and restored before the next test.
            global.history.replaceState(null, "", path)
            flushSync { root = mountApplication() }
            withContext(Dispatchers.Default) {
                repeat(100) {
                    val text = element.textContent.orEmpty()
                    if (text.contains(expectedText, ignoreCase = true) && (!loadsApi || text.contains(LOADED_RANKING))) return@withContext
                    delay(10)
                }
            }
            if (loadsApi) {
                // The homepage loads the ranking, the week's news and the coming week's events.
                val expected = listOf("/api/rankings/weekly", "/api/news/weekly", "/api/events")
                assertEquals(expected.map { "${window.location.origin}$it" }.sorted(), requests.sorted())
                // The ranking lists are hidden for now; the footer still reports the loaded edition.
                assertTrue(element.textContent.orEmpty().contains(LOADED_RANKING))
            } else {
                assertTrue(requests.isEmpty())
            }
            assertTrue(element.textContent.orEmpty().contains(expectedText, ignoreCase = true), expectedText)
            afterMount()
        } finally {
            flushSync { root?.unmount() }
            element.remove()
            global.fetch = originalFetch
            global.history.replaceState(null, "", originalPath)
        }
    }
}

private const val LOADED_RANKING = "Last updated 2026-09-28 · 7 articles reviewed"
