package net.productberlin.presentation

import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.presentation.news.TickerLine
import net.productberlin.presentation.news.kicker
import net.productberlin.presentation.news.toFrontPage
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.time
import react.dom.html.ReactHTML.ul
import react.useState
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.button

external interface WeeklyNewsSectionProps : Props {
    var news: WeeklyNews

    /** The coming week's events, shown as the news column's last section; none hides it. */
    var events: List<StartupEvent>?
}

/**
 * "This week in Berlin startups", laid out like a front page: a lead story, a row of teasers, then a compact ticker
 * with everything else, one line per company, a band of earlier stories worth catching up on, and the coming week's
 * events. Size and position
 * show importance; the colours stay neutral.
 */
val WeeklyNewsSection =
    FC<WeeklyNewsSectionProps> { props ->
        val page = props.news.toFrontPage()
        var showAll by useState(false)
        section {
            className = ClassName("weekly-news")
            ariaLabelledBy = ElementId("weekly-news-title")
            div {
                className = ClassName("ranking-meta")
                h2 {
                    id = ElementId("weekly-news-title")
                    +"Berlin startup news"
                }
                span { +props.news.weekLabel }
            }
            // Front page: lead and teasers on the left, the ticker as a sidebar on the right (stacked on phones).
            div {
                className = ClassName("news-front")
                div {
                    className = ClassName("news-main")
                    page.lead?.let { lead ->
                        Story {
                            story = lead
                            size = "lead"
                        }
                    }
                    if (page.teasers.isNotEmpty()) {
                        div {
                            className = ClassName("news-teasers")
                            page.teasers.forEach {
                                Story {
                                    key = Key(it.id)
                                    story = it
                                    size = "teaser"
                                }
                            }
                        }
                    }
                    if (page.catchUp.isNotEmpty()) {
                        CatchUpBand {
                            stories = page.catchUp
                            label = props.news.earlierLabel
                        }
                    }
                }
                if (page.ticker.isNotEmpty()) {
                    aside {
                        className = ClassName("news-ticker")
                        ariaLabel = "Ticker"
                        h3 { +"Ticker" }
                        ul {
                            val lines = if (showAll) page.ticker else page.ticker.take(TICKER_PREVIEW)
                            lines.forEach {
                                Ticker {
                                    key = Key(it.company)
                                    line = it
                                }
                            }
                        }
                        if (page.ticker.size > TICKER_PREVIEW) {
                            button {
                                type = ButtonType.button
                                className = ClassName("news-ticker-more")
                                ariaExpanded = showAll
                                onClick = { showAll = !showAll }
                                +(if (showAll) "Show less" else "Show all ${page.ticker.size}")
                            }
                        }
                    }
                }
            }
            // Full width under the news: the coming week as a planner.
            props.events?.takeIf { it.isNotEmpty() }?.let {
                EventsSection {
                    events = it
                    nested = true
                }
            }
        }
    }

private const val TICKER_PREVIEW = 8

private external interface StoryProps : Props {
    var story: WeeklyStory
    var size: String
}

/** A lead or teaser: kicker, company mark, headline linking to the first report, publisher and other outlets. */
private val Story =
    FC<StoryProps> { props ->
        val story = props.story
        article {
            className = ClassName("news-${props.size}")
            // The company's logo stands in for a photo: press photos belong to the publishers.
            CompanyMark {
                companyId = story.companyId
                companyName = story.companies.first()
                logoUrl = story.logoUrl
                logoFallbackUrl = story.logoFallbackUrl
                variant = "${props.size}-mark"
            }
            div {
                className = ClassName("news-body")
                story.kicker()?.let {
                    p {
                        className = ClassName("news-kicker")
                        +it
                    }
                }
                h3 { Headline { this.story = story } }
                p {
                    className = ClassName("news-byline")
                    span {
                        className = ClassName("news-company")
                        +story.companies.joinToString(" · ")
                    }
                    +" · ${story.source}"
                    val others = story.alsoIn.size
                    if (others > 0) +" · also $others more ${if (others == 1) "outlet" else "outlets"}"
                    +" · "
                    time {
                        dateTime = story.publishedAt.take(10)
                        +shortDate(story.publishedAt)
                    }
                }
            }
        }
    }

private external interface TickerProps : Props {
    var line: TickerLine
}

/** One company's line: newest headline, "+N more" that unfolds its other stories. */
private val Ticker =
    FC<TickerProps> { props ->
        val line = props.line
        var open by useState(false)
        li {
            className = ClassName("news-ticker-line")
            p {
                className = ClassName("news-ticker-meta")
                time {
                    dateTime = line.newest.publishedAt.take(10)
                    +shortDate(line.newest.publishedAt)
                }
                +" · "
                span {
                    className = ClassName("news-company")
                    +line.company
                }
            }
            Headline { story = line.newest }
            if (line.more.isNotEmpty()) {
                button {
                    type = ButtonType.button
                    className = ClassName("news-ticker-count")
                    ariaExpanded = open
                    onClick = { open = !open }
                    +(if (open) "less" else "+${line.more.size} more")
                }
            }
            if (open) {
                ul {
                    line.more.forEach {
                        li {
                            key = Key(it.id)
                            Headline { story = it }
                        }
                    }
                }
            }
        }
    }
