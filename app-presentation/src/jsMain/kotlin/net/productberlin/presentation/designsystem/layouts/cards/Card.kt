package net.productberlin.presentation.designsystem.layouts.cards

import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface CardProps : PropsWithChildren {
    var elevated: Boolean?
    var rounded: Boolean?
}

val Card =
    FC<CardProps> { props ->
        div {
            className =
                ClassName(
                    "pb-card" +
                        (if (props.elevated == true) " pb-card-elevated" else "") +
                        (if (props.rounded == true) " pb-card-rounded" else ""),
                )
            +props.children
        }
    }
