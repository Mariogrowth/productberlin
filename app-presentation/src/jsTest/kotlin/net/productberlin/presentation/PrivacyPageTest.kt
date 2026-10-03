package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.productberlin.presentation.testing.ComponentTest
import react.create

class PrivacyPageTest : ComponentTest() {
    @Test
    fun namesTheOperatorWithAWorkingContactLinkAndNoPlaceholders() {
        render(PrivacyPage.create())
        val text = container.textContent.orEmpty()
        assertTrue(text.contains("Mario Garcia"))
        assertEquals("mailto:Mario@product.berlin", container.querySelector("a[href^='mailto:']")?.getAttribute("href"))
        assertFalse(text.contains("["), "No placeholder text remains")
        assertTrue(text.contains("send you emails about the latest news on the city"))
        assertEquals("/", container.querySelector("a.privacy-home")?.getAttribute("href"))
    }
}
