package net.productberlin.presentation

import net.productberlin.domain.entity.EventKind
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.presentation.events.cleanEventTitle
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.h4
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

external interface EventsSectionProps : Props {
    var events: List<StartupEvent>

    /** True under the news; false when it stands alone (a week without news). */
    var nested: Boolean?
}

/**
 * "Events in Berlin": the coming week as a planner, one column per day in Berlin time. Each day shows up to three
 * events, founders' events first, and unfolds the rest; tech and AI talks are marked. Titles link to the organiser's
 * page, where registration happens.
 */
val EventsSection =
    FC<EventsSectionProps> { props ->
        section {
            className = ClassName("news-events")
            ariaLabelledBy = ElementId("news-events-title")
            div {
                className = ClassName("news-band-head")
                if (props.nested == true) {
                    h3 {
                        id = ElementId("news-events-title")
                        +"Events in Berlin"
                    }
                } else {
                    h2 {
                        id = ElementId("news-events-title")
                        +"Events in Berlin"
                    }
                }
                span { +"Next 7 days" }
            }
            div {
                className = ClassName("event-days")
                props.events
                    .sortedBy { it.startsAt }
                    .groupBy { BerlinTime.day(it.startsAt) }
                    .forEach { (day, events) ->
                        EventDay {
                            key = Key(day)
                            this.events = events
                        }
                    }
            }
        }
    }

private external interface EventDayProps : Props {
    var events: List<StartupEvent>
}

/** One day: the date as a large number with its weekday, then its events in time order. */
private val EventDay =
    FC<EventDayProps> { props ->
        var open by useState(false)
        val first = props.events.first().startsAt
        val preview =
            props.events
                .sortedWith(compareBy<StartupEvent> { it.kind != EventKind.Startup }.thenBy { it.startsAt })
                .take(PER_DAY)
                .toSet()
        val shown = if (open) props.events else props.events.filter { it in preview }
        div {
            className = ClassName("event-day")
            h4 {
                span {
                    className = ClassName("event-day-number")
                    +BerlinTime.dayOfMonth(first)
                }
                span {
                    className = ClassName("event-day-name")
                    +BerlinTime.weekdayAndMonth(first)
                }
            }
            ul {
                shown.forEach { event ->
                    li {
                        key = Key(event.id)
                        className = ClassName("event-row")
                        time {
                            dateTime = event.startsAt
                            +BerlinTime.time(event.startsAt)
                        }
                        div {
                            val title = cleanEventTitle(event.title)
                            val link = event.url?.takeIf { SAFE_LINK.matches(it) }
                            if (link != null) {
                                a {
                                    href = link
                                    if (title != event.title) this.title = event.title
                                    +title
                                }
                            } else {
                                span { +title }
                            }
                            p {
                                +listOfNotNull(
                                    event.organiser,
                                    event.venue?.takeIf { it != event.organiser },
                                    EventKind.Tech.title.takeIf { event.kind == EventKind.Tech },
                                ).joinToString(" · ")
                            }
                        }
                    }
                }
            }
            val hidden = props.events.size - preview.size
            if (hidden > 0) {
                button {
                    type = ButtonType.button
                    className = ClassName("news-ticker-count")
                    ariaExpanded = open
                    onClick = { open = !open }
                    +(if (open) "less" else "+$hidden more")
                }
            }
        }
    }

private const val PER_DAY = 3
private val SAFE_LINK = Regex("^https://[^\\s/<>]+/[^\\s<>]*$")
