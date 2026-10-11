package net.productberlin.worker.keycheck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.entity.KeyType
import net.productberlin.worker.keyCheck

class SiteScannerTest {
    /** Assembled at runtime so no source file contains a credential-shaped literal. */
    private val openAiKey = "sk-" + "proj-" + "aB3dE5fG7hJ9kL2mN4pQ6rS8tUvW1xY0zA2bC4dE"
    private val publishable = "pk_" + "live_" + "aB3dE5fG7hJ9kL2mN4pQ6rS8t"

    private class FakeSite(
        val pages: Map<String, FetchedPage>,
    ) : PublicFetcher {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String): FetchedPage {
            requested += url
            return pages[url] ?: FetchedPage(404, null, "")
        }
    }

    private fun ok(body: String) = FetchedPage(200, null, body)

    private val html =
        """
        <html><head>
        <script type="module" src="/assets/index-8f2a.js"></script>
        <link rel="modulepreload" href="https://www.myapp.com/assets/vendor.js">
        <script src="https://cdn.thirdparty.com/widget.js"></script>
        <script>window.STRIPE = "$publishable";</script>
        </head></html>
        """.trimIndent()

    @Test
    fun readsThePageItsOwnScriptsAndTheirSourceMapsButNotThirdParties() =
        runTest {
            val site =
                FakeSite(
                    mapOf(
                        "https://myapp.com/" to ok(html),
                        "https://myapp.com/assets/index-8f2a.js" to ok("const a=1;\n//# sourceMappingURL=index-8f2a.js.map"),
                        "https://www.myapp.com/assets/vendor.js" to ok("export const v=2;"),
                        "https://myapp.com/assets/index-8f2a.js.map" to ok("""{"sourcesContent":["const key = '$openAiKey';"]}"""),
                    ),
                )
            val scan = (SiteScanner(site).scan("myapp.com") as KeyScanResult.Completed).scan
            assertEquals(4, scan.filesScanned)
            assertEquals(1, scan.sourceMaps)
            assertTrue(site.requested.none { it.contains("thirdparty") }, "third-party scripts are not the site's")
            val secret = scan.secrets.single()
            assertEquals(KeyType.OpenAi, secret.type)
            assertEquals("/assets/index-8f2a.js.map", secret.location)
            assertEquals(KeyType.StripePublishable, scan.findings.single { it != secret }.type)
            assertEquals("/", scan.findings.single { it != secret }.location, "inline script in the page")
        }

    @Test
    fun redirectsAreFollowedOnlyWithinTheSiteOverHttps() =
        runTest {
            val within =
                FakeSite(
                    mapOf(
                        "https://myapp.com/" to FetchedPage(301, "https://www.myapp.com/", ""),
                        "https://www.myapp.com/" to ok("<p>hi</p>"),
                    ),
                )
            assertTrue(SiteScanner(within).scan("myapp.com") is KeyScanResult.Completed)
            for (target in listOf(
                "https://evil.example/",
                "http://www.myapp.com/",
                "https://myapp.com.evil.example/",
                "https://myapp.com:8443/",
            )) {
                val away = FakeSite(mapOf("https://myapp.com/" to FetchedPage(302, target, "")))
                assertEquals(KeyScanResult.Unreachable, SiteScanner(away).scan("myapp.com"), target)
                assertEquals(listOf("https://myapp.com/"), away.requested, "never fetched $target")
            }
        }

    @Test
    fun aSiteThatFailsOrNeverAnswersIsUnreachable() =
        runTest {
            assertEquals(KeyScanResult.Unreachable, SiteScanner(FakeSite(emptyMap())).scan("myapp.com"))
            val throwing = PublicFetcher { error("DNS lookup failed") }
            assertEquals(KeyScanResult.Unreachable, SiteScanner(throwing).scan("myapp.com"))
            val loop = FakeSite(mapOf("https://myapp.com/" to FetchedPage(302, "https://myapp.com/", "")))
            assertEquals(KeyScanResult.Unreachable, SiteScanner(loop).scan("myapp.com"))
            assertEquals(4, loop.requested.size, "at most three redirects")
        }

    @Test
    fun atMostFortyFilesAreReadPerScan() =
        runTest {
            val scripts = (1..60).joinToString("") { """<script src="/s$it.js"></script>""" }
            val site = FakeSite(mapOf("https://myapp.com/" to ok(scripts)) + (1..60).associate { "https://myapp.com/s$it.js" to ok("x") })
            val scan = (SiteScanner(site).scan("myapp.com") as KeyScanResult.Completed).scan
            assertEquals(40, site.requested.size)
            assertEquals(40, scan.filesScanned)
        }

    @Test
    fun theEndpointValidatesInputAndNeverReturnsAFullKey() =
        runTest {
            val site =
                FakeSite(
                    mapOf("https://myapp.com/" to ok("<script>const k='$openAiKey'</script>")),
                )
            val response = keyCheck("GET", "https://MyApp.com/pricing", site)
            assertEquals(200, response.status, response.body)
            assertTrue(openAiKey !in response.body)
            assertTrue(response.body.contains("\"type\":\"OpenAi\""), response.body)
            assertTrue(response.body.contains("sk-proj-…"), response.body)
            for (input in listOf(null, "localhost", "10.0.0.1", "myapp.com:22")) {
                assertEquals(400, keyCheck("GET", input, site).status, input.toString())
            }
            assertEquals(405, keyCheck("POST", "myapp.com", site).status)
            assertEquals(502, keyCheck("GET", "unknown.example", site).status)
        }
}
