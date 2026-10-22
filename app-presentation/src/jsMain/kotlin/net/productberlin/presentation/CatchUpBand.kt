package net.productberlin.presentation

import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.presentation.news.kicker
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.time
import react.dom.html.ReactHTML.ul
import web.cssom.ClassName
import web.dom.ElementId

external interface CatchUpBandProps : Props {
    var stories: List<WeeklyStory>
    var label: String?
}

/** Earlier stories still worth knowing, one compact row each: kicker, headline, company, outlets and date. */
val CatchUpBand =
    FC<CatchUpBandProps> { props ->
        section {
            className = ClassName("news-catch-up")
            ariaLabelledBy = ElementId("news-catch-up-title")
            div {
                className = ClassName("news-band-head")
                h3 {
                    id = ElementId("news-catch-up-title")
                    +"Catch up"
                }
                props.label?.let { span { +it } }
            }
            ul {
                props.stories.forEach { story ->
                    li {
                        key = Key(story.id)
                        p {
                            className = ClassName("news-kicker")
                            +(story.kicker() ?: "")
                        }
                        div {
                            Headline { this.story = story }
                            p {
                                className = ClassName("news-byline")
                                span {
                                    className = ClassName("news-company")
                                    +story.companies.joinToString(" · ")
                                }
                                +" · ${story.source}"
                                val others = story.alsoIn.size
                                if (others > 0) +" · +$others ${if (others == 1) "outlet" else "outlets"}"
                                +" · "
                                time {
                                    dateTime = story.publishedAt.take(10)
                                    +shortDate(story.publishedAt)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
