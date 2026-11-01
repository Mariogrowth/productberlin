package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository
import net.productberlin.domain.usecase.SubscribeToNewsletter
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.html.HTMLButtonElement
import web.html.HTMLInputElement
import web.window.window

class NewsletterSignupTest : ComponentTest() {
    private class Fake(
        var result: SubscriptionResult = SubscriptionResult.ConfirmationSent,
    ) : NewsletterRepository {
        val requested = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun requestSubscription(email: EmailAddress): SubscriptionResult {
            requested += email.value
            gate?.await()
            return result
        }
    }

    private val enterValue =
        js(
            """(element, value) => {
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, value);
                element.dispatchEvent(new Event('input', { bubbles: true }));
            }""",
        )

    private fun field() = assertNotNull(container.querySelector(".pb-email-signup-pill input")).unsafeCast<HTMLInputElement>()

    private fun button() = assertNotNull(container.querySelector(".pb-email-signup button")).unsafeCast<HTMLButtonElement>()

    private fun message() = container.querySelector(".pb-email-signup-message")?.textContent.orEmpty()

    private fun mount(repository: Fake) = render(NewsletterSignup.create { subscribe = SubscribeToNewsletter(repository) })

    private suspend fun submit(value: String) {
        flushSync { enterValue(field(), value) }
        flushSync { button().click() }
        waitFor { message().isNotEmpty() }
    }

    private suspend fun waitFor(condition: () -> Boolean) =
        withContext(Dispatchers.Default) {
            repeat(100) {
                if (condition()) return@withContext
                delay(10)
            }
        }

    @Test
    fun sentConfirmationAsksToVerifyBeneathThePillWhichStays() =
        runTest {
            val repository = Fake()
            mount(repository)
            submit(" name@domain.de ")
            assertEquals("Verify you are human, check your inbox", message())
            assertEquals(listOf("name@domain.de"), repository.requested)
            assertNotNull(container.querySelector(".pb-email-signup-pill"))
        }

    @Test
    fun malformedAddressesAreFlaggedWithoutARequestAndClearOnEdit() =
        runTest {
            val repository = Fake()
            mount(repository)
            submit("name@")
            assertEquals("Enter a valid email address.", message())
            assertEquals("true", field().getAttribute("aria-invalid"))
            assertEquals(emptyList(), repository.requested)
            flushSync { enterValue(field(), "name@domain.de") }
            assertEquals("", message())
        }

    @Test
    fun serviceFailuresInviteARetry() =
        runTest {
            val repository = Fake(SubscriptionResult.Unavailable)
            mount(repository)
            submit("name@domain.de")
            assertEquals("Something went wrong. Please try again.", message())
            repository.result = SubscriptionResult.ConfirmationSent
            flushSync { button().click() }
            waitFor { message().startsWith("Verify") }
            assertEquals("Verify you are human, check your inbox", message())
            assertEquals(2, repository.requested.size)
        }

    @Test
    fun buttonIsDisabledWhileSending() =
        runTest {
            val repository = Fake().apply { gate = CompletableDeferred() }
            mount(repository)
            flushSync { enterValue(field(), "name@domain.de") }
            flushSync { button().click() }
            waitFor { button().disabled }
            assertEquals("Sending…", button().textContent)
            repository.gate?.complete(Unit)
            waitFor { message().isNotEmpty() }
            assertEquals(false, button().disabled)
        }

    @Test
    fun filledSpamTrapLooksSuccessfulButSendsNothing() =
        runTest {
            val repository = Fake()
            mount(repository)
            val trap = assertNotNull(container.querySelector("input[name='website']")).unsafeCast<HTMLInputElement>()
            assertEquals("-1", trap.getAttribute("tabindex"))
            assertEquals("true", trap.getAttribute("aria-hidden"))
            flushSync { enterValue(trap, "https://spam.test") }
            submit("name@domain.de")
            assertEquals("Verify you are human, check your inbox", message())
            assertEquals(emptyList(), repository.requested)
        }

    @Test
    fun linksToThePrivacyPolicy() =
        runTest {
            mount(Fake())
            assertEquals("/privacy", container.querySelector("a.pb-email-signup-privacy")?.getAttribute("href"))
        }

    @Test
    fun onWideScreensThePrivacyLinkNeverMakesTheBarTaller() =
        runTest {
            mount(Fake())
            // Phones stack title, field and link; the wide layout keeps the link beside the field.
            if (window.innerWidth <= 640) return@runTest
            val privacy = assertNotNull(container.querySelector("a.pb-email-signup-privacy"))
            val withLink = bar().getBoundingClientRect().height
            privacy.asDynamic().style.display = "none"
            assertEquals(withLink, bar().getBoundingClientRect().height)
        }

    private fun bar() = assertNotNull(container.querySelector("#newsletter.newsletter-bar"))

    private fun shown() =
        bar()
            .getAttribute("class")
            .orEmpty()
            .split(" ")
            .contains("is-shown")

    private val scrollTo = js("(y) => { window.scrollTo(0, y); window.dispatchEvent(new Event('scroll')); }")

    @Test
    fun theBarSlidesUpOnceTheReaderScrollsAndStays() =
        runTest {
            val spacer = web.dom.document.createElement("div")
            spacer.asDynamic().style.height = "4000px"
            web.dom.document.body
                .appendChild(spacer)
            try {
                scrollTo(0)
                mount(Fake())
                assertEquals(false, shown(), "hidden at the top of the page")
                assertEquals("Newsletter sign-up", bar().getAttribute("aria-label"))
                scrollTo(600)
                waitFor { shown() }
                assertEquals(true, shown())
                scrollTo(0)
                waitFor { !shown() }
                assertEquals(true, shown(), "it stays once shown")
            } finally {
                scrollTo(0)
                spacer.remove()
            }
        }

    @Test
    fun closingHidesTheBarUntilTheNewsletterLinkIsFollowed() =
        runTest {
            window.location.hash = "#newsletter"
            try {
                mount(Fake())
                waitFor { shown() }
                assertEquals(true, shown(), "the header link opens it")
                val close = assertNotNull(container.querySelector("button.newsletter-close")).unsafeCast<HTMLButtonElement>()
                assertEquals("Close newsletter sign-up", close.getAttribute("aria-label"))
                flushSync { close.click() }
                assertEquals(false, shown())
                assertEquals("", window.location.hash, "closing clears the link's target so it can be followed again")
                window.location.hash = "#newsletter"
                waitFor { shown() }
                assertEquals(true, shown())
            } finally {
                window.asDynamic().history.replaceState(null, "", window.location.pathname)
            }
        }
}
