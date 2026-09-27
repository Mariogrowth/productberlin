package net.productberlin.presentation.testing

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import react.ReactNode
import react.dom.client.Root
import react.dom.client.createRoot
import react.dom.flushSync
import web.dom.document
import web.html.HTMLElement

/** Mount components into a real browser DOM and release every React root after each test. */
abstract class ComponentTest {
    protected lateinit var container: HTMLElement
    private lateinit var root: Root

    @BeforeTest
    fun mountRoot() {
        container = document.createElement("div")
        document.body.appendChild(container)
        root = createRoot(container)
    }

    protected fun render(node: ReactNode) {
        flushSync { root.render(node) }
    }

    @AfterTest
    fun unmountRoot() {
        flushSync { root.unmount() }
        container.remove()
    }
}
