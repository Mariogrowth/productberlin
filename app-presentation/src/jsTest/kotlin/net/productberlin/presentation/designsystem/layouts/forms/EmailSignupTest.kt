package net.productberlin.presentation.designsystem.layouts.forms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.create
import react.dom.flushSync
import web.cssom.getComputedStyle

class EmailSignupTest : DesignSystemTest() {
    private var submits = 0

    private fun signup(
        status: EmailSignupStatus = EmailSignupStatus.Idle,
        message: String? = null,
    ) = EmailSignup.create {
        title = "Get the list"
        value = "a@b.co"
        onValueChange = {}
        onSubmit = { submits += 1 }
        actionLabel = "Notify me"
        submittingLabel = "Sending…"
        note = "One email a week."
        this.status = status
        this.message = message
    }

    @Test
    fun visibleTitleLabelsAnEmailFieldAndSubmitDoesNotNavigate() {
        themed()
        render(signup())
        val field = input()
        val label = assertNotNull(container.querySelector("label"))
        assertEquals(field.getAttribute("id"), label.getAttribute("for"))
        assertEquals("Get the list", label.textContent)
        assertEquals("email", field.getAttribute("type"))
        assertEquals("email", field.getAttribute("autocomplete"))
        assertEquals("submit", button().getAttribute("type"))
        flushSync { button().click() }
        assertEquals(1, submits)
        assertTrue(container.isConnected, "Submitting stays on the page")
    }

    @Test
    fun invalidStateMarksTheFieldAndDescribesItWithAnAnnouncedMessage() {
        themed()
        render(signup(EmailSignupStatus.Invalid, "Enter a valid email address."))
        val field = input()
        val message = assertNotNull(container.querySelector("#${field.getAttribute("aria-describedby")}"))
        assertEquals("true", field.getAttribute("aria-invalid"))
        assertEquals("Enter a valid email address.", message.textContent)
        assertEquals("polite", message.getAttribute("aria-live"))
        assertEquals("rgb(180, 35, 24)", getComputedStyle(message).color, "Error text uses the error token")
    }

    @Test
    fun submittingDisablesTheActionAndIgnoresRepeatedSubmits() {
        themed()
        render(signup(EmailSignupStatus.Submitting))
        assertTrue(button().disabled)
        assertEquals("Sending…", button().textContent)
        assertTrue(input().readOnly)
        flushSync { container.querySelector("form").unsafeCast<web.html.HTMLFormElement>().requestSubmit() }
        assertEquals(0, submits)
    }

    @Test
    fun successReplacesTheFieldWithTheMessageInTheSameLiveRegion() {
        themed()
        render(signup(EmailSignupStatus.Succeeded, "Check your inbox to confirm."))
        assertNull(container.querySelector("input"))
        assertNull(container.querySelector(".pb-email-signup-note"))
        val message = assertNotNull(container.querySelector(".pb-email-signup-message"))
        assertEquals("Check your inbox to confirm.", message.textContent)
        assertEquals("polite", message.getAttribute("aria-live"))
        assertEquals("rgb(22, 134, 59)", getComputedStyle(message).color, "Success text uses the success token")
    }

    @Test
    fun pillIsRoundAndTheActionMeetsTheMinimumTargetSize() {
        themed()
        render(signup())
        val pill = assertNotNull(container.querySelector(".pb-email-signup-pill"))
        assertEquals("999px", getComputedStyle(pill).borderTopLeftRadius)
        assertTrue(button().getBoundingClientRect().height >= 40.0)
        assertEquals("rgb(255, 255, 255)", getComputedStyle(button()).backgroundColor, "The action is white")
    }

    @Test
    fun focusingTheFieldUsesANeutralEdgeWithoutBrandColour() {
        themed()
        render(signup())
        val pill = assertNotNull(container.querySelector(".pb-email-signup-pill"))
        flushSync { input().focus() }
        assertEquals("rgb(102, 106, 112)", getComputedStyle(pill).borderTopColor)
        assertEquals("none", getComputedStyle(pill).boxShadow)
        assertEquals("none", getComputedStyle(input()).outlineStyle)
        assertEquals("rgb(229, 230, 227)", getComputedStyle(button()).borderTopColor, "The action's edge is unchanged")
    }

    @Test
    fun focusingTheActionMarksOnlyThePillEdge() {
        themed()
        render(signup())
        val pill = assertNotNull(container.querySelector(".pb-email-signup-pill"))
        flushSync { button().focus() }
        assertEquals("rgb(102, 106, 112)", getComputedStyle(pill).borderTopColor)
        assertEquals("none", getComputedStyle(button()).outlineStyle)
        assertEquals("rgb(229, 230, 227)", getComputedStyle(button()).borderTopColor)
    }
}
