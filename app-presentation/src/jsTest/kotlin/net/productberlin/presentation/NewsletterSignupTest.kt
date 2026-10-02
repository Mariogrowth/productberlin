package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.html.HTMLButtonElement
import web.html.HTMLInputElement

class NewsletterSignupTest : ComponentTest() {
    private val enterValue =
        js(
            """(element, value) => {
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, value);
                element.dispatchEvent(new Event('input', { bubbles: true }));
            }""",
        )

    private fun field() = assertNotNull(container.querySelector("input")).unsafeCast<HTMLInputElement>()

    private fun submit() = flushSync { assertNotNull(container.querySelector("button")).unsafeCast<HTMLButtonElement>().click() }

    private fun message() = container.querySelector(".pb-email-signup-message")?.textContent

    @Test
    fun rejectsMalformedAddressesUntilTheVisitorEditsTheField() {
        render(NewsletterSignup.create())
        for (value in listOf("", "   ", "name", "name@", "name@domain", "a b@c.de")) {
            flushSync { enterValue(field(), value) }
            submit()
            assertEquals("Enter a valid email address.", message(), value)
            assertEquals("true", field().getAttribute("aria-invalid"))
        }
        flushSync { enterValue(field(), "name@domain.de") }
        assertEquals("", message())
        assertEquals(null, field().getAttribute("aria-invalid"))
    }

    @Test
    fun theFieldIsLabelledByTheFrequencyAndUnsubscribePromiseWithoutASeparateNote() {
        render(NewsletterSignup.create())
        assertEquals("One email a week. Unsubscribe anytime.", container.querySelector("label")?.textContent)
        assertEquals(field().getAttribute("id"), container.querySelector("label")?.getAttribute("for"))
        assertEquals(null, container.querySelector(".pb-email-signup-note"))
        assertEquals(
            1,
            container.textContent
                .orEmpty()
                .split("Unsubscribe anytime")
                .size - 1,
        )
    }

    @Test
    fun validAddressIsNotConfirmedWhileDeliveryIsNotConnected() {
        render(NewsletterSignup.create())
        flushSync { enterValue(field(), " name@domain.de ") }
        submit()
        assertEquals("Sign-ups aren’t open yet. Check back soon.", message())
        assertEquals("name@domain.de", field().value.trim())
    }
}
