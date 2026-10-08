package net.productberlin.presentation.designsystem.layouts.news

import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import net.productberlin.presentation.designsystem.layouts.cards.Card
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface NewsFeedProps : Props {
    var stories: List<NewsStory>
    var isMock: Boolean?
    var onCollapse: (() -> Unit)?
}

/** Publisher identity and linked headlines; no invented images or article excerpts. */
val NewsFeed =
    FC<NewsFeedProps> { props ->
        div {
            className = ClassName("pb-news-feed")
            Card {
                rounded = true
                if (props.stories.isEmpty()) p { +"No recent stories available." }
                props.stories.forEach { story ->
                    article {
                        key = Key(story.id)
                        className = ClassName("news-story")
                        div {
                            className = ClassName("pb-news-publisher")
                            span {
                                className = ClassName("pb-news-avatar")
                                ariaHidden = true
                                +story.publisher.take(2).uppercase()
                            }
                            +story.publisher
                        }
                        h4 {
                            story.originalHeadline?.let { title = "Original: $it" }
                            val link = story.url?.takeIf { Regex("^https://[^\\s/<>]+/[^\\s<>]*$").matches(it) }
                            if (link != null) {
                                a {
                                    href = link
                                    +story.headline
                                }
                            } else {
                                +story.headline
                            }
                        }
                        span {
                            className = ClassName("story-meta")
                            +(story.date + if (props.isMock == true) " · Sample story" else "")
                        }
                        story.summary?.takeIf { it.isNotBlank() }?.let { p { +it } }
                    }
                }
                props.onCollapse?.let { collapse ->
                    Button {
                        variant = ButtonVariant.Secondary
                        onClick = collapse
                        +"Show less"
                    }
                }
            }
        }
    }
