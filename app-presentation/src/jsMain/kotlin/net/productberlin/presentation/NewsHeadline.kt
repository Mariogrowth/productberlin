package net.productberlin.presentation

import net.productberlin.domain.entity.WeeklyStory
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.span

internal external interface HeadlineProps : Props {
    var story: WeeklyStory
}

/** The English headline (original on hover), linking to the first report when its link is safe. */
internal val Headline =
    FC<HeadlineProps> { props ->
        val story = props.story
        val text = story.translatedHeadline ?: story.headline
        val link = story.safeLink()
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

/** The first report's link when it is an absolute HTTPS URL, else null. */
internal fun WeeklyStory.safeLink(): String? = url?.takeIf { SAFE_LINK.matches(it) }

private val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>]*$")
