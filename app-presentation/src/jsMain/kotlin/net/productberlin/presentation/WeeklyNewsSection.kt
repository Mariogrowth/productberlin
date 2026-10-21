package net.productberlin.presentation

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
}

/**
 * "This week in Berlin startups", laid out like a front page: a lead story, a row of teasers, then a compact ticker
 * with everything else, one line per company. Size and position show importance; the colours stay neutral.
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
                    +"This week in Berlin startups"
                }
                span { +props.news.weekLabel }
            }
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
            if (page.ticker.isNotEmpty()) {
                div {
                    className = ClassName("news-ticker")
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
            story.kicker()?.let {
                p {
                    className = ClassName("news-kicker")
                    +it
                }
            }
            h3 { Headline { this.story = story } }
            div {
                className = ClassName("news-byline")
                CompanyMark {
                    companyId = story.companyId
                    companyName = story.companies.first()
                    logoUrl = story.logoUrl
                    logoFallbackUrl = story.logoFallbackUrl
                    variant = "story-mark"
                }
                span {
                    span {
                        className = ClassName("news-company")
                        +story.companies.joinToString(" · ")
                    }
                    +" · ${story.source}"
                    val others = story.alsoIn.size
                    if (others > 0) +" · also $others more ${if (others == 1) "outlet" else "outlets"}"
                }
                time {
                    dateTime = story.publishedAt.take(10)
                    +shortDate(story.publishedAt)
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
            time {
                dateTime = line.newest.publishedAt.take(10)
                +shortDate(line.newest.publishedAt)
            }
            div {
                span {
                    className = ClassName("news-company")
                    +line.company
                }
                +" "
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
    }

private external interface HeadlineProps : Props {
    var story: WeeklyStory
}

/** The English headline (original on hover), linking to the first report when its link is safe. */
private val Headline =
    FC<HeadlineProps> { props ->
        val story = props.story
        val text = story.translatedHeadline ?: story.headline
        val link = story.url?.takeIf { SAFE_LINK.matches(it) }
        if (link != null) {
            a {
                href = link
                story.translatedHeadline?.let { title = "Original: ${story.headline}" }
                +text
            }
        } else {
            span { +text }
        }
    }

private val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>]*$")
