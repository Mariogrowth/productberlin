package net.productberlin

import net.productberlin.di.createAppContainer
import net.productberlin.presentation.App
import net.productberlin.presentation.PrivacyPage
import net.productberlin.presentation.designsystem.showcase.DesignSystemShowcase
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document
import web.history.history
import web.url.URL
import web.window.window

fun main() {
    mountApplication()
}

internal fun mountApplication(): Root {
    val root = createRoot(requireNotNull(document.getElementById(ElementId("root"))))
    when (window.location.pathname.trimEnd('/')) {
        "/design-system" -> {
            root.render(DesignSystemShowcase.create())
        }

        "/privacy" -> {
            root.render(PrivacyPage.create())
        }

        else -> {
            val container = createAppContainer()
            val confirmed = consumeSubscriptionConfirmation()
            root.render(
                App.create {
                    getRanking = container.koin.get()
                    subscribe = container.koin.get()
                    subscriptionConfirmed = confirmed
                },
            )
        }
    }
    return root
}

/** Brevo returns confirmed subscribers to `/?subscribed=1`; the marker is removed so reloads and shares stay clean. */
private fun consumeSubscriptionConfirmation(): Boolean {
    val url = URL(window.location.href)
    if (url.searchParams.get("subscribed") != "1") return false
    url.searchParams.delete("subscribed")
    history.replaceState(null, "", url.pathname + url.search + url.hash)
    return true
}
