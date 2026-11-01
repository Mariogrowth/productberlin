package net.productberlin.presentation

import net.productberlin.domain.state.RankingState
import net.productberlin.domain.usecase.GetUpcomingEvents
import net.productberlin.domain.usecase.GetWeeklyNews
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
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.footer
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.button

external interface AppProps : Props {
    var getRanking: GetWeeklyRanking
    var getNews: GetWeeklyNews
    var getEvents: GetUpcomingEvents
    var subscribe: SubscribeToNewsletter
    var subscriptionConfirmed: Boolean?

    /** The startup and scaleup lists are hidden for now; the news sidebar still shows the top five. */
    var showRankings: Boolean?
}

val App =
    FC<AppProps> { props ->
        var attempt by useState(0)
        var showConfirmation by useState(props.subscriptionConfirmed == true)
        val state = useWeeklyRanking(props.getRanking, attempt)
        val news = useWeeklyNews(props.getNews)
        val events = useUpcomingEvents(props.getEvents)
        val ranking = (state as? RankingState.Ready)?.ranking
        val showRankings = props.showRankings == true

        a {
            className = ClassName("skip-link")
            href = "#content"
            +"Skip to content"
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
        // Only live editions show news and events; demo editions, quiet weeks and failed loads show no section. Events
        // close the news section, or stand alone in a week without news.
        val liveEvents = events.takeIf { ranking?.isMock == false }.orEmpty()
        val showNews = ranking?.isMock == false && news != null && news.stories.isNotEmpty()
        SiteHeader {
            sections =
                listOfNotNull(
                    ("News" to "news").takeIf { showNews },
                    ("Rankings" to "rankings").takeIf { showRankings },
                    ("Events" to "events").takeIf { liveEvents.isNotEmpty() },
                    "Newsletter" to "newsletter",
                )
        }
        main {
            id = ElementId("content")
            // News first, at front-page width; the rankings follow as their own section.
            if (showNews && news != null) {
                div {
                    className = ClassName("front-width")
                    WeeklyNewsSection {
                        this.news = news
                        this.events = liveEvents
                        this.ranking = ranking?.startups
                        linkToRankings = showRankings
                    }
                }
            } else if (liveEvents.isNotEmpty()) {
                div {
                    className = ClassName("front-width")
                    EventsSection { this.events = liveEvents }
                }
            }
            if (showRankings) {
                section {
                    id = ElementId("rankings")
                    className = ClassName("page-content rankings")
                    div {
                        className = ClassName("ranking-meta")
                        h2 { +"Berlin startups this week" }
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
                    // Scaleups are ranked by the same press momentum, separately, so startups keep the main list.
                    if (ranking != null && ranking.scaleups.isNotEmpty()) {
                        section {
                            className = ClassName("scaleups")
                            ariaLabelledBy = ElementId("scaleups-title")
                            div {
                                className = ClassName("ranking-meta")
                                h2 {
                                    id = ElementId("scaleups-title")
                                    +"Scaleups in the news"
                                }
                                span { +"Ranked separately" }
                            }
                            div {
                                className = ClassName("ranking-card scaleup-card")
                                ol {
                                    className = ClassName("startup-list")
                                    ranking.scaleups.forEachIndexed { index, scaleup ->
                                        StartupRow {
                                            key = Key(scaleup.id)
                                            company = scaleup
                                            position = index + 1
                                            isMock = ranking.isMock
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Room at the end of the page for the docked sign-up bar, so it never covers the footer.
            div {
                className = ClassName("page-content page-end")
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
                    a {
                        className = ClassName("footer-privacy")
                        href = "/privacy"
                        +"Privacy policy"
                    }
                }
            }
        }
    }

/** Shown once, after a visitor returns from the double opt-in link in the confirmation email. */
internal const val SUBSCRIPTION_CONFIRMED =
    "Email Verified. Expect a weekly roundup of Berlin’s top tech news, startup highlights, and ecosystem updates 🚀"
