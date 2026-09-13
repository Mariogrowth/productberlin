package net.productberlin.presentation

import net.productberlin.domain.entity.Startup
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.button

external interface StartupRowProps : Props {
    var company: Startup
    var position: Int
}

val StartupRow =
    FC<StartupRowProps> { props ->
        val company = props.company
        var expanded by useState(false)
        li {
            className = ClassName("startup-row")
            span {
                className = ClassName("rank")
                +"${props.position}."
            }
            div {
                className = ClassName("company-mark mark-${company.id}")
                ariaHidden = true
                +(
                    when (company.id) {
                        "almedia" -> "a"
                        "aignostics" -> "ai"
                        "n26" -> "N26"
                        "parloa" -> "P"
                        "sennder" -> "s"
                        "enpal" -> "en"
                        "trade-republic" -> "TR"
                        "deepset" -> "d"
                        "personio" -> "p"
                        else -> "e"
                    }
                )
            }
            div {
                className = ClassName("company-info")
                div {
                    className = ClassName("company-title")
                    h3 { +company.name }
                    val movement = company.movement
                    span {
                        className =
                            ClassName(
                                when {
                                    movement == null -> "movement new"
                                    movement > 0 -> "movement up"
                                    movement < 0 -> "movement down"
                                    else -> "movement steady"
                                },
                            )
                        title =
                            when {
                                movement == null -> "New this week"
                                movement > 0 -> "Up $movement places"
                                movement < 0 -> "Down ${-movement} places"
                                else -> "No change this week"
                            }
                        ariaLabel = title
                        +(
                            when {
                                movement == null -> "NEW"
                                movement > 0 -> "▴ $movement"
                                movement < 0 -> "▾ ${-movement}"
                                else -> "—"
                            }
                        )
                    }
                }
                p {
                    className = ClassName("company-description")
                    +company.description
                }
            }
            button {
                type = ButtonType.button
                className = ClassName("why-button")
                ariaLabel = "Why ${company.name} is ranked ${props.position}"
                ariaExpanded = expanded
                ariaControls = ElementId("reason-${company.id}")
                onClick = { expanded = !expanded }
                +"Why"
                span {
                    className = ClassName("chevron")
                    ariaHidden = true
                }
            }
            div {
                id = ElementId("reason-${company.id}")
                className = ClassName(if (expanded) "why-content" else "why-content is-collapsed")
                span {
                    className = ClassName("category")
                    +company.category
                }
                p {
                    className = ClassName("ranking-reason")
                    +company.reason
                }
                company.news.forEach { story ->
                    article {
                        key = Key(story.id)
                        className = ClassName("news-story")
                        span {
                            className = ClassName("story-meta")
                            +"${story.source} · ${story.publishedAt} · Sample story"
                        }
                        h4 { +story.headline }
                    }
                }
            }
        }
    }
