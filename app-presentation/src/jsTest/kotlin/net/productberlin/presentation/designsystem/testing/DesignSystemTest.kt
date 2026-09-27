package net.productberlin.presentation.designsystem.testing

import kotlin.test.assertNotNull
import net.productberlin.presentation.testing.ComponentTest
import web.cssom.ClassName
import web.html.HTMLButtonElement
import web.html.HTMLInputElement

/** Shared DOM helpers for design-system component, layout and catalogue tests. */
abstract class DesignSystemTest : ComponentTest() {
    protected fun button(selector: String = "button") = assertNotNull(container.querySelector(selector)).unsafeCast<HTMLButtonElement>()

    protected fun input(selector: String = "input") = assertNotNull(container.querySelector(selector)).unsafeCast<HTMLInputElement>()

    protected fun themed() {
        container.className = ClassName("pb-theme")
    }
}
