package net.productberlin

import net.productberlin.di.createAppContainer
import net.productberlin.presentation.App
import net.productberlin.presentation.designsystem.showcase.DesignSystemShowcase
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document
import web.window.window

fun main() {
    mountApplication()
}

internal fun mountApplication(): Root {
    val root = createRoot(requireNotNull(document.getElementById(ElementId("root"))))
    if (window.location.pathname.trimEnd('/') == "/design-system") {
        root.render(DesignSystemShowcase.create())
    } else {
        val container = createAppContainer()
        root.render(App.create { getRanking = container.koin.get() })
    }
    return root
}
