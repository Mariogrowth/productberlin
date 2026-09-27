package net.productberlin.presentation.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.components.badges.Badge
import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import net.productberlin.presentation.designsystem.components.inputs.TextField
import net.productberlin.presentation.designsystem.components.navigation.NavigationItem
import net.productberlin.presentation.designsystem.components.text.Text
import net.productberlin.presentation.designsystem.components.text.TextStyle
import net.productberlin.presentation.designsystem.components.text.TextWeight
import net.productberlin.presentation.designsystem.components.toggles.Checkbox
import net.productberlin.presentation.designsystem.components.toggles.Radio
import net.productberlin.presentation.designsystem.components.toggles.Switch
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.FC
import react.Props
import react.create
import react.dom.flushSync
import react.dom.html.ReactHTML.div
import react.useState
import web.cssom.getComputedStyle
import web.html.HTMLInputElement

class BasicComponentsTest : DesignSystemTest() {
    @Test
    fun buttonsHaveNativeDisabledBehaviorAndNeverSubmitByDefault() {
        var calls = 0
        ButtonVariant.entries.forEach { style ->
            render(
                Button.create {
                    variant = style
                    onClick = { calls++ }
                    +"Save"
                },
            )
            assertEquals("button", button().getAttribute("type"))
            flushSync { button().click() }
        }
        assertEquals(3, calls)
        render(
            Button.create {
                disabled = true
                onClick = { calls++ }
                +"Save"
            },
        )
        flushSync { button().click() }
        assertEquals(3, calls)
        assertTrue(button().disabled)
    }

    @Test
    fun typographyAndPaletteUseTheMeasuredReferenceValues() {
        themed()
        render(
            Text.create {
                variant = TextStyle.Display
                weight = TextWeight.Bold
                +"Search"
            },
        )
        val display = getComputedStyle(assertNotNull(container.querySelector(".pb-text")))
        assertEquals("56px", display.fontSize)
        assertEquals("800", display.fontWeight)
        assertEquals("56px", display.lineHeight)
        assertEquals("rgb(17, 17, 17)", display.color)
        listOf(
            TextStyle.Heading1 to "36px",
            TextStyle.Heading2 to "26px",
            TextStyle.Heading3 to "20px",
            TextStyle.Body to "15px",
            TextStyle.Label to "13px",
            TextStyle.Caption to "11px",
        ).forEach { (variant, size) ->
            render(
                Text.create {
                    this.variant = variant
                    +"Sample"
                },
            )
            assertEquals(size, getComputedStyle(assertNotNull(container.querySelector(".pb-text"))).fontSize)
        }
        render(Button.create { +"Action" })
        assertEquals("rgb(90, 205, 238)", getComputedStyle(button()).backgroundColor)
        assertEquals("44px", getComputedStyle(button()).minHeight)
    }

    @Test
    fun fieldsLinkLabelsAndErrorsAndKeepInstanceIdsUnique() {
        render(
            div.create {
                repeat(2) {
                    TextField {
                        label = "Company"
                        value = ""
                        onValueChange = {}
                        error = "Enter a company"
                        placeholder = "Name"
                    }
                }
            },
        )
        val fields = container.querySelectorAll("input")
        val first = fields.item(0).unsafeCast<HTMLInputElement>()
        val second = fields.item(1).unsafeCast<HTMLInputElement>()
        assertNotEquals(first.id, second.id)
        assertEquals(first.getAttribute("id"), container.querySelector("label")?.getAttribute("for"))
        assertEquals("true", first.getAttribute("aria-invalid"))
        val messageId = first.getAttribute("aria-describedby")
        assertEquals("Enter a company", container.querySelector("[id='$messageId']")?.textContent)
    }

    @Test
    fun fieldsReportTypedValuesAndSupportingActionsAreDisabledWithTheInput() {
        var received = ""
        var helpCalls = 0
        val field =
            FC<Props> {
                var text by useState("")
                TextField {
                    label = "Search"
                    value = text
                    search = true
                    onValueChange = {
                        text = it
                        received = it
                    }
                }
            }
        render(field.create())
        val enterValue =
            js(
                """(element, value) => {
                    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, value);
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                }""",
            )
        flushSync { enterValue(input(), "Berlin") }
        assertEquals("Berlin", received)
        assertEquals("Berlin", input().value)
        render(
            TextField.create {
                label = "Company"
                value = ""
                onValueChange = {}
                actionLabel = "Explain field"
                onAction =
                    { helpCalls++ }
            },
        )
        assertEquals("Explain field", button().getAttribute("aria-label"))
        flushSync { button().click() }
        assertEquals(1, helpCalls)
        render(
            TextField.create {
                label = "Company"
                value = ""
                onValueChange = {}
                disabled = true
                actionLabel = "Explain field"
                onAction =
                    { helpCalls++ }
            },
        )
        flushSync { button().click() }
        assertEquals(1, helpCalls)
        assertTrue(input().disabled)
    }

    @Test
    fun checkboxAndSwitchToggleAndDisabledControlsStayUnchanged() {
        var checked = false
        val controls =
            FC<Props> {
                var value by useState(false)
                Checkbox {
                    label = "Save"
                    this.checked = value
                    onCheckedChange = {
                        value = it
                        checked = it
                    }
                }
                Switch {
                    label = "Updates"
                    this.checked = value
                    onCheckedChange = {
                        value = it
                        checked = it
                    }
                }
            }
        render(controls.create())
        flushSync { input().click() }
        assertTrue(checked)
        assertTrue(input("[role='switch']").checked)
        flushSync { input("[role='switch']").click() }
        assertFalse(checked)
        render(
            Checkbox.create {
                label = "Disabled"
                this.checked = false
                disabled = true
                onCheckedChange = { checked = it }
            },
        )
        flushSync { input().click() }
        assertFalse(checked)
    }

    @Test
    fun radiosShareANativeGroupAndReflectTheSelectedOption() {
        val radios =
            FC<Props> {
                var selected by useState("weekly")
                listOf("weekly", "monthly").forEach { choice ->
                    Radio {
                        label = choice
                        name = "frequency"
                        value = choice
                        checked =
                            selected == choice
                        onCheckedChange = { selected = choice }
                    }
                }
            }
        render(radios.create())
        flushSync { input("[value='monthly']").click() }
        assertTrue(input("[value='monthly']").checked)
        assertFalse(input("[value='weekly']").checked)
    }

    @Test
    fun navigationBadgeAndIconKeepTheirAccessibleSemantics() {
        render(
            NavigationItem.create {
                label = "Companies"
                href = "/companies"
                active = true
            },
        )
        assertEquals("page", container.querySelector("a")?.getAttribute("aria-current"))
        assertEquals("/companies", container.querySelector("a")?.getAttribute("href"))
        assertEquals("true", container.querySelector(".pb-icon")?.getAttribute("aria-hidden"))
        assertEquals("1.5", container.querySelector("svg")?.getAttribute("stroke-width"))
        assertEquals("24", container.querySelector("svg")?.getAttribute("width"))
        render(
            Badge.create {
                verified = true
                +"Verified"
            },
        )
        assertEquals("Verified", container.textContent)
        assertNotNull(container.querySelector(".pb-badge-verified"))
    }
}
