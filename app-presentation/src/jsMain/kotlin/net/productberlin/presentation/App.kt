package net.productberlin.presentation

import net.productberlin.domain.state.RankingState
import net.productberlin.domain.usecase.GetWeeklyRanking
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
}

val App =
    FC<AppProps> { props ->
        var attempt by useState(0)
        val state = useWeeklyRanking(props.getRanking, attempt)

        a {
            className = ClassName("skip-link")
            href = "#rankings"
            +"Skip to rankings"
        }
        header {
            className = ClassName("masthead")
            div {
                className = ClassName("edition")
                span { className = ClassName("live-dot") }
                +"A weekly pulse on the city"
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
                span { +((state as? RankingState.Ready)?.ranking?.weekLabel ?: "September 7–13, 2026") }
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
                    span {
                        +"The names. The news."
                        br {}
                        +"The reasons why."
                    }
                }
            }
            footer {
                div {
                    className = ClassName("demo-label")
                    span { className = ClassName("demo-dot") }
                    +"Demo edition"
                }
                p { +"Sample rankings & fictional news. Real Berlin energy." }
                details {
                    className = ClassName("about-ranking")
                    summary { +"How the list works" }
                    p {
                        +(
                            "A preview of a weekly startup roundup. Positions, movement, and stories are mocked for this design. " +
                                "Later, source workers will collect public company updates and news for a transparent, " +
                                "evidence-backed ranking."
                        )
                    }
                }
                span {
                    className = ClassName("made-in")
                    +"Made for the city that keeps making."
                }
            }
        }
    }
