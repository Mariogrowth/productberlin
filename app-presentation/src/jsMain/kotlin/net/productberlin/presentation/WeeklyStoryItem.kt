package net.productberlin.presentation

import net.productberlin.domain.entity.WeeklyStory
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.time
import web.cssom.ClassName

external interface WeeklyStoryItemProps : Props {
    var story: WeeklyStory
}

/** One story: companies, date, English headline (original on hover) linking to the first report, and other outlets. */
val WeeklyStoryItem =
    FC<WeeklyStoryItemProps> { props ->
        val story = props.story
        article {
            className = ClassName("weekly-story")
            div {
                className = ClassName("weekly-story-top")
                span {
                    className = ClassName("weekly-story-company")
                    +story.companies.joinToString(" · ")
                }
                time {
                    dateTime = story.publishedAt.take(10)
                    +shortDate(story.publishedAt)
                }
            }
            h4 {
                story.translatedHeadline?.let { title = "Original: ${story.headline}" }
                val headline = story.translatedHeadline ?: story.headline
                val link = story.url?.takeIf { SAFE_LINK.matches(it) }
                if (link != null) {
                    a {
                        href = link
                        +headline
                    }
                } else {
                    +headline
                }
            }
            p {
                className = ClassName("weekly-story-source")
                +story.source
                if (story.alsoIn.isNotEmpty()) +" · also ${story.alsoIn.take(3).joinToString(", ")}"
            }
        }
    }

private val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>]*$")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-12T…" → "Oct 12". */
internal fun shortDate(iso: String): String {
    val month = iso.substring(5, 7).toIntOrNull() ?: return iso.take(10)
    val day = iso.substring(8, 10).toIntOrNull() ?: return iso.take(10)
    return "${MONTHS.getOrElse(month - 1) { "" }} $day".trim()
}
