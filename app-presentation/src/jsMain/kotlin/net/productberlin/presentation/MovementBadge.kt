package net.productberlin.presentation

import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface MovementBadgeProps : Props {
    /** Places moved since last week; null for a company new to the list. */
    var movement: Int?
}

/** A ranked company's change since last week: "NEW", "▴ 4", "▾ 2" or "—", with a spoken label. */
val MovementBadge =
    FC<MovementBadgeProps> { props ->
        val movement = props.movement
        span {
            className =
                ClassName(
                    when {
                        movement == null -> "movement new"
                        movement > 0 -> "movement up"
                        movement < 0 -> "movement down"
                        else -> "movement steady"
                    },
                )
            title =
                when {
                    movement == null -> "New this week"
                    movement > 0 -> "Up $movement places"
                    movement < 0 -> "Down ${-movement} places"
                    else -> "No change this week"
                }
            ariaLabel = title
            +(
                when {
                    movement == null -> "NEW"
                    movement > 0 -> "▴ $movement"
                    movement < 0 -> "▾ ${-movement}"
                    else -> "—"
                }
            )
        }
    }
