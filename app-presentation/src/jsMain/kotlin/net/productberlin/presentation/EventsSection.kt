package net.productberlin.presentation

import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.presentation.designsystem.layouts.filters.FilterBar
import net.productberlin.presentation.designsystem.layouts.filters.FilterOption
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.time
import react.useState
import web.cssom.ClassName
import web.dom.ElementId

external interface EventsSectionProps : Props {
    var events: List<StartupEvent>
}

/**
 * "Startup events in Berlin": the coming week from curated organisers, grouped by day in Berlin time. Startup events
 * are shown first by default; tech and AI talks, or everything, are one click away. Registration happens on the
 * organiser's page.
 */
val EventsSection =
    FC<EventsSectionProps> { props ->
        var selected by useState(EventKind.Startup.name)
        val counts = props.events.groupingBy { it.kind }.eachCount()
        val options =
            EventKind.entries.map { FilterOption(it.name, "${it.title} (${counts[it] ?: 0})") } +
                FilterOption(ALL, "All (${props.events.size})")
        val shown = props.events.filter { selected == ALL || it.kind.name == selected }
        section {
            className = ClassName("berlin-events")
            ariaLabelledBy = ElementId("berlin-events-title")
            div {
                className = ClassName("ranking-meta")
                h2 {
                    id = ElementId("berlin-events-title")
                    +"Startup events in Berlin"
                }
                span { +"Next 7 days" }
            }
            div {
                className = ClassName("pb-theme weekly-news-filters")
                FilterBar {
                    label = "Event types"
                    this.options = options
                    selectedId = selected
                    onSelect = { selected = it }
                }
            }
            if (shown.isEmpty()) {
                p {
                    className = ClassName("status-message")
                    +"Nothing listed for the coming week yet."
                }
            }
            for ((day, events) in shown.groupBy { BerlinTime.day(it.startsAt) }) {
                div {
                    key = Key(day)
                    className = ClassName("berlin-events-day")
                    h3 { +day }
                    events.forEach { event ->
                        article {
                            key = Key(event.id)
                            className = ClassName("berlin-event")
                            time {
                                dateTime = event.startsAt
                                +BerlinTime.time(event.startsAt)
                            }
                            div {
                                h4 {
                                    val link = event.url?.takeIf { SAFE_LINK.matches(it) }
                                    if (link != null) {
                                        a {
                                            href = link
                                            +event.title
                                        }
                                    } else {
                                        +event.title
                                    }
                                }
                                p { +listOfNotNull(event.organiser, event.venue?.takeIf { it != event.organiser }).joinToString(" · ") }
                            }
                        }
                    }
                }
            }
        }
    }

private const val ALL = "all"
private val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>]*$")
