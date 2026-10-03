package net.productberlin.presentation

import net.productberlin.domain.state.RankingState
import net.productberlin.domain.usecase.GetWeeklyRanking
import net.productberlin.domain.usecase.SubscribeToNewsletter
import net.productberlin.presentation.designsystem.layouts.feedback.Banner
import react.FC
import react.Key
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.footer
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.summary
import react.useState
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.button

external interface AppProps : Props {
    var getRanking: GetWeeklyRanking
    var subscribe: SubscribeToNewsletter
    var subscriptionConfirmed: Boolean?
}

val App =
    FC<AppProps> { props ->
        var attempt by useState(0)
        var showConfirmation by useState(props.subscriptionConfirmed == true)
        val state = useWeeklyRanking(props.getRanking, attempt)
        val ranking = (state as? RankingState.Ready)?.ranking

        a {
            className = ClassName("skip-link")
            href = "#rankings"
            +"Skip to rankings"
        }
        if (showConfirmation) {
            div {
                className = ClassName("pb-theme confirmation")
                Banner {
                    message = SUBSCRIPTION_CONFIRMED
                    onDismiss = { showConfirmation = false }
                }
            }
        }
        header {
            className = ClassName("masthead")
            div {
                className = ClassName("edition")
                span { className = ClassName("live-dot") }
                +"Your weekly news on the city"
            }
            h1 {
                +"Product"
                span {
                    className = ClassName("brand-dot")
                    +"."
                }
                +"berlin"
            }
            p {
                className = ClassName("tagline")
                +"Good things are being built here."
            }
        }
        main {
            id = ElementId("rankings")
            className = ClassName("page-content")
            div {
                className = ClassName("ranking-meta")
                h2 { +"The weekly ten" }
                span { +((state as? RankingState.Ready)?.ranking?.weekLabel ?: "This week") }
            }
            div {
                className = ClassName("ranking-layout")
                section {
                    className = ClassName("ranking-card")
                    when (val current = state) {
                        RankingState.Loading -> {
                            p {
                                className = ClassName("status-message")
                                role = AriaRole.status
                                +"Finding the city’s bright ideas…"
                            }
                        }

                        RankingState.Failed -> {
                            div {
                                className = ClassName("status-message")
                                role = AriaRole.alert
                                p { +"We couldn’t load this week’s list." }
                                button {
                                    type = ButtonType.button
                                    onClick = { attempt += 1 }
                                    +"Try again"
                                }
                            }
                        }

                        is RankingState.Ready -> {
                            if (current.ranking.startups.isEmpty()) {
                                p {
                                    className = ClassName("status-message")
                                    +"A quiet week. Check back for fresh ideas."
                                }
                            } else {
                                ol {
                                    className = ClassName("startup-list")
                                    current.ranking.startups.forEachIndexed { index, startup ->
                                        StartupRow {
                                            key = Key(startup.id)
                                            company = startup
                                            position = index + 1
                                            isMock = current.ranking.isMock
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                aside {
                    className = ClassName("margin-note")
                    p {
                        +"Berlin’s hottest"
                        br {}
                        +"startups this week"
                    }
                    img {
                        src = "arrow.svg"
                        alt = ""
                        className = ClassName("note-arrow")
                    }
                }
            }
            NewsletterSignup { subscribe = props.subscribe }
            footer {
                div {
                    className = ClassName("demo-label")
                    span { className = ClassName("demo-dot") }
                    +(if (ranking?.isMock == true) "Demo edition" else "Google News · Updated weekly")
                }
                p {
                    +(
                        if (ranking?.isMock == true) {
                            "Sample rankings & fictional news. Real Berlin energy."
                        } else {
                            ranking?.updatedAt?.let { "Last updated ${it.take(10)} · ${ranking.articleCount ?: 0} articles reviewed" }
                                ?: "Berlin startups in the news."
                        }
                    )
                }
                details {
                    className = ClassName("about-ranking")
                    summary { +"How the list works" }
                    p {
                        +(
                            if (ranking?.isMock == true) {
                                "A preview with sample rankings and fictional stories."
                            } else {
                                "This metric ranks up to ten catalogued Berlin startups by their press relevance counts " +
                                    "over the previous seven UTC days, refreshing every Monday at 06:00 UTC " +
                                    "as an indicator of media visibility rather than company quality."
                            }
                        )
                    }
                }
                span {
                    className = ClassName("made-in")
                    +"Made for the city that keeps making."
                }
                a {
                    className = ClassName("footer-link")
                    href = "/privacy"
                    +"Privacy"
                }
            }
        }
    }

/** Shown once, after a visitor returns from the double opt-in link in the confirmation email. */
internal const val SUBSCRIPTION_CONFIRMED =
    "Email Verified. Expect a weekly roundup of Berlin’s top tech news, startup highlights, and ecosystem updates 🚀"
