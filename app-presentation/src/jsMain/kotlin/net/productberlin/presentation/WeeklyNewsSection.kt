package net.productberlin.presentation

import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.presentation.news.kicker
import net.productberlin.presentation.news.toFrontPage
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.time
import web.cssom.ClassName
import web.dom.ElementId

external interface WeeklyNewsSectionProps : Props {
    var news: WeeklyNews

    /** The coming week's events, shown as the news column's last section; none hides it. */
    var events: List<StartupEvent>?

    /** This week's startup ranking, for the sidebar's top five; none leaves only the deals. */
    var ranking: List<Startup>?

    /** Whether the full ranking lists follow, so the sidebar can link to them. */
    var linkToRankings: Boolean?
}

/**
 * "Berlin startup news", laid out like a front page: a lead story, a row of teasers and a band of earlier stories worth
 * catching up on, a sidebar with the ranking's top five and the period's deals, and the coming week's events. Size and
 * position show importance; the colours stay neutral.
 */
val WeeklyNewsSection =
    FC<WeeklyNewsSectionProps> { props ->
        val page = props.news.toFrontPage()
        section {
            id = ElementId("news")
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
            // Front page: lead and teasers on the left, the sidebar on the right (stacked on phones).
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
                val ranking = props.ranking.orEmpty()
                if (ranking.isNotEmpty() || page.deals.isNotEmpty()) {
                    NewsSidebar {
                        this.ranking = ranking
                        linkToFullList = props.linkToRankings
                        deals = page.deals
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
