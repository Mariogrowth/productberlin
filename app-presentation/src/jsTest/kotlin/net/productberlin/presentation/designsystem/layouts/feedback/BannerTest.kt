package net.productberlin.presentation.designsystem.layouts.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.create
import react.dom.flushSync
import web.cssom.getComputedStyle

class BannerTest : DesignSystemTest() {
    private var dismissed = 0

    private fun banner() =
        Banner.create {
            message = "Email Verified."
            onDismiss = { dismissed += 1 }
        }

    @Test
    fun announcesTheMessagePolitelyWithADecorativeIconAndALabelledDismiss() {
        themed()
        render(banner())
        val root = assertNotNull(container.querySelector(".pb-banner"))
        assertEquals("status", root.getAttribute("role"))
        assertEquals("Email Verified.", root.querySelector(".pb-banner-message")?.textContent)
        assertEquals("true", root.querySelector(".pb-icon")?.getAttribute("aria-hidden"))
        assertEquals("Dismiss", button().getAttribute("aria-label"))
        assertEquals("button", button().getAttribute("type"))
        flushSync { button().click() }
        assertEquals(1, dismissed)
    }

    @Test
    fun isNeutralScrollsWithThePageAndHasAComfortableDismissTarget() {
        themed()
        render(banner())
        val style = getComputedStyle(assertNotNull(container.querySelector(".pb-banner")))
        assertEquals("rgb(240, 242, 239)", style.backgroundColor, "Neutral subtle surface")
        assertEquals("rgb(17, 17, 17)", style.color)
        assertEquals("static", style.position, "Not sticky or fixed")
        val target = button().getBoundingClientRect()
        assertTrue(target.width >= 44.0 && target.height >= 44.0)
    }

    @Test
    fun customDismissLabel() {
        themed()
        render(
            Banner.create {
                message = "Saved"
                onDismiss = {}
                dismissLabel = "Close notice"
            },
        )
        assertEquals("Close notice", button().getAttribute("aria-label"))
    }
}
