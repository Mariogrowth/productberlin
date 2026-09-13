package net.productberlin

import net.productberlin.di.createAppContainer
import net.productberlin.presentation.App
import react.create
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document

fun main() {
    val container = createAppContainer()
    val root = createRoot(requireNotNull(document.getElementById(ElementId("root"))))
    root.render(App.create { getRanking = container.koin.get() })
}
