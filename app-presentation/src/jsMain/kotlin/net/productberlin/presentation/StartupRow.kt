package net.productberlin.presentation

import net.productberlin.domain.entity.Startup
import net.productberlin.presentation.designsystem.layouts.news.NewsFeed
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.MediaQuery
import web.cssom.matchMedia
import web.dom.ElementId
import web.dom.FocusOptions
import web.html.ButtonType
import web.html.HTMLButtonElement
import web.html.button
import web.scroll.ScrollBehavior
import web.scroll.ScrollIntoViewOptions
import web.scroll.ScrollLogicalPosition
import web.scroll.auto
import web.scroll.nearest
import web.scroll.smooth
import web.window.window

external interface StartupRowProps : Props {
    var company: Startup
    var isMock: Boolean?
    var position: Int
}

val StartupRow =
    FC<StartupRowProps> { props ->
        val company = props.company
        var expanded by useState(false)
        val whyButton = useRef<HTMLButtonElement>(null)
        li {
            className = ClassName("startup-row")
            span {
                className = ClassName("rank")
                +"${props.position}."
            }
            CompanyMark {
                companyId = company.id
                companyName = company.name
                logoUrl = company.logoUrl
                logoFallbackUrl = company.logoFallbackUrl
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
                ref = whyButton
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
                // Stays mounted so its height can animate; inert keeps the hidden news out of tab order and the a11y tree.
                inert = !expanded
                div {
                    className = ClassName("why-inner")
                    span {
                        className = ClassName("category")
                        +company.category
                    }
                    div {
                        className = ClassName("pb-theme")
                        NewsFeed {
                            stories = company.news.map { it.toNewsStory() }
                            isMock = props.isMock != false
                            onCollapse = {
                                expanded = false
                                whyButton.current?.let(::returnFocusWithoutJump)
                            }
                        }
                    }
                }
            }
        }
    }

/** Moves focus back without the browser's instant jump, scrolling gently only when "Why" is off-screen. */
private fun returnFocusWithoutJump(why: HTMLButtonElement) {
    why.focus(FocusOptions(preventScroll = true))
    val box = why.getBoundingClientRect()
    if (box.top >= 0 && box.bottom <= window.innerHeight) return
    val reduceMotion = matchMedia(MediaQuery("(prefers-reduced-motion: reduce)")).matches
    why.scrollIntoView(
        ScrollIntoViewOptions(
            behavior = if (reduceMotion) ScrollBehavior.auto else ScrollBehavior.smooth,
            block = ScrollLogicalPosition.nearest,
        ),
    )
}
