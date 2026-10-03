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

    private fun button() = assertNotNull(container.querySelector("button")).unsafeCast<HTMLButtonElement>()

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
}
