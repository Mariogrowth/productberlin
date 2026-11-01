package net.productberlin.presentation

import net.productberlin.domain.entity.Startup
import net.productberlin.presentation.news.Deal
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import web.cssom.ClassName

internal external interface NewsSidebarProps : Props {
    /** This week's startup ranking; the sidebar shows its top five. */
    var ranking: List<Startup>
    var deals: List<Deal>

    /** Whether the full ranking lists are on the page to link down to. */
    var linkToFullList: Boolean?
}

/**
 * The front page's sidebar, two short lists that each say why a line matters: the top of this week's ranking with
 * each company's movement, linking down to the full list, and the period's funding rounds and exits with amounts.
 */
internal val NewsSidebar =
    FC<NewsSidebarProps> { props ->
        aside {
            className = ClassName("news-sidebar")
            ariaLabel = "At a glance"
            val top = props.ranking.take(TOP)
            if (top.isNotEmpty()) {
                section {
                    className = ClassName("sidebar-block sidebar-top")
                    div {
                        className = ClassName("news-band-head")
                        h3 { +"Top ${top.size} this week" }
                        if (props.linkToFullList == true) {
                            a {
                                href = "#rankings"
                                +"Full list ↓"
                            }
                        }
                    }
                    ol {
                        top.forEachIndexed { index, startup ->
                            li {
                                key = Key(startup.id)
                                span {
                                    className = ClassName("rank")
                                    +"${index + 1}"
                                }
                                CompanyMark {
                                    companyId = startup.id
                                    companyName = startup.name
                                    logoUrl = startup.logoUrl
                                    logoFallbackUrl = startup.logoFallbackUrl
                                    variant = "sidebar-mark"
                                }
                                span {
                                    className = ClassName("news-company")
                                    +startup.name
                                }
                                MovementBadge { movement = startup.movement }
                            }
                        }
                    }
                }
            }
            if (props.deals.isNotEmpty()) {
                section {
                    className = ClassName("sidebar-block sidebar-deals")
                    div {
                        className = ClassName("news-band-head")
                        h3 { +"Deals" }
                        span { +"Funding & exits" }
                    }
                    ul {
                        props.deals.forEach { deal ->
                            li {
                                key = Key(deal.story.id)
                                val link = deal.story.safeLink()
                                val row: react.ChildrenBuilder.() -> Unit = {
                                    span {
                                        className = ClassName("news-company")
                                        +deal.company
                                    }
                                    span {
                                        className = ClassName("deal-kind")
                                        +deal.kind
                                    }
                                    span {
                                        className = ClassName("deal-amount")
                                        +(deal.amount ?: "—")
                                    }
                                }
                                if (link != null) {
                                    a {
                                        href = link
                                        title = deal.story.translatedHeadline ?: deal.story.headline
                                        row()
                                    }
                                } else {
                                    div { row() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private const val TOP = 5
