package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.entity.KeyType
import net.productberlin.domain.repository.KeyScanRepository
import net.productberlin.domain.usecase.CheckSiteForKeys
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.events.Event
import web.events.EventType
import web.html.HTMLButtonElement
import web.html.HTMLFormElement
import web.html.HTMLInputElement

class KeyCheckPageTest : ComponentTest() {
    private val scanned = mutableListOf<String>()

    private fun show(result: KeyScanResult) {
        val repository =
            object : KeyScanRepository {
                override suspend fun scan(domain: String): KeyScanResult {
                    scanned += domain
                    return result
                }
            }
        render(KeyCheckPage.create { checkSite = CheckSiteForKeys(repository) })
    }

    private fun submit(domain: String) {
        val input = container.querySelector("#key-check-domain")!!.unsafeCast<HTMLInputElement>()
        // React tracks the value through its own setter, so set it the way a typing user would.
        val setter = js("Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set")
        setter.call(input, domain)
        flushSync { input.dispatchEvent(Event(EventType("input"), js("({bubbles:true})"))) }
        flushSync { container.querySelector("form")!!.unsafeCast<HTMLFormElement>().requestSubmit() }
    }

    private suspend fun awaitUi(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        assertTrue(predicate(), "Expected UI state was not rendered")
    }

    @Test
    fun theFieldHasAVisibleLabelAndTheButtonWaitsForInput() {
        show(KeyScanResult.Unavailable)
        assertEquals("Your domain", container.querySelector("label[for='key-check-domain']")?.textContent)
        assertTrue(container.querySelector("button[type='submit']")!!.unsafeCast<HTMLButtonElement>().disabled)
        assertNull(container.querySelector(".key-check-report"))
    }

    @Test
    fun aLeakIsExplainedWithAMaskedHintAndAFixUrgentFirst() =
        runTest {
            show(
                KeyScanResult.Completed(
                    KeyScan(
                        "myapp.com",
                        4,
                        1,
                        listOf(
                            KeyFinding(KeyType.StripePublishable, "pk_live_…8t2a", "/"),
                            KeyFinding(KeyType.OpenAi, "sk-proj-…dE4x", "/assets/index.js"),
                        ),
                    ),
                ),
            )
            submit("https://myapp.com/pricing")
            awaitUi { container.querySelector(".key-check-report") != null }
            assertEquals(listOf("myapp.com"), scanned)
            assertEquals("myapp.com: 1 urgent problem", container.querySelector(".key-check-report h2")?.textContent)
            assertTrue(container.textContent.orEmpty().contains("4 public files checked · 1 source map"))
            assertNotNull(container.querySelector(".key-check-aside"), "public source maps are pointed out")
            val findings = container.querySelectorAll(".key-finding")
            assertEquals(2, findings.length)
            val first = findings.item(0)!!
            assertEquals("Urgent: secret key", first.querySelector(".key-finding-verdict")?.textContent)
            assertEquals("OpenAI API key", first.querySelector("h3")?.textContent)
            assertTrue(first.textContent.orEmpty().contains("sk-proj-…dE4x in /assets/index.js"))
            assertTrue(first.textContent.orEmpty().contains("platform.openai.com → API keys"))
            assertNotNull(first.querySelector(".key-finding-copy"))
            val second = findings.item(1)!!
            assertEquals("Fine to be public", second.querySelector(".key-finding-verdict")?.textContent)
            assertEquals("Stripe publishable key", second.querySelector("h3")?.textContent)
            assertNull(second.querySelector(".key-finding-copy"), "nothing to fix for a publishable key")
        }

    @Test
    fun aCleanSiteSaysSo() =
        runTest {
            show(KeyScanResult.Completed(KeyScan("product.berlin", 3, 1, emptyList())))
            submit("product.berlin")
            awaitUi { container.querySelector(".key-check-report") != null }
            assertEquals("product.berlin: no API keys found", container.querySelector(".key-check-report h2")?.textContent)
        }

    @Test
    fun badInputAndUnreachableSitesGetAPlainMessage() =
        runTest {
            show(KeyScanResult.Unreachable)
            submit("not a domain")
            awaitUi { container.querySelector("[role=alert]") != null }
            assertTrue(
                container
                    .querySelector("[role=alert]")!!
                    .textContent
                    .orEmpty()
                    .contains("doesn’t look like a domain"),
            )
            assertEquals(emptyList(), scanned, "invalid input never reaches the checker")
            submit("myapp.com")
            awaitUi {
                container
                    .querySelector("[role=alert]")
                    ?.textContent
                    .orEmpty()
                    .contains("couldn’t reach")
            }
        }
}
