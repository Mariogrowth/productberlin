package net.productberlin.presentation.designsystem.layouts.ranking

import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.span
import react.useId
import react.useState
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.button
import web.html.`true`

external interface RankedResultRowProps : Props {
    var result: RankedResult
}

val RankedResultRow =
    FC<RankedResultRowProps> { props ->
        val result = props.result
        var expanded by useState(false)
        val reasonId = useId()
        li {
            className = ClassName("pb-result")
            span {
                className = ClassName("pb-result-rank")
                +"${result.rank}."
            }
            span {
                className = ClassName("pb-result-avatar")
                ariaHidden = true
                +result.name.take(1)
            }
            div {
                className = ClassName("pb-result-identity")
                div {
                    className = ClassName("pb-result-name")
                    +result.name
                    val movement = result.movement
                    span {
                        className = ClassName("pb-result-movement${if (movement != null && movement > 0) " pb-positive" else ""}")
                        ariaLabel =
                            when {
                                movement == null -> "New this week"
                                movement > 0 -> "Up $movement places"
                                movement < 0 -> "Down ${-movement} places"
                                else -> "No change this week"
                            }
                        +when {
                            movement == null -> "New"
                            movement > 0 -> "↗ $movement"
                            movement < 0 -> "↘ ${-movement}"
                            else -> "—"
                        }
                    }
                }
                div {
                    className = ClassName("pb-result-description")
                    +result.description
                }
            }
            button {
                type = ButtonType.button
                className = ClassName("pb-disclosure")
                ariaLabel = "Why ${result.name} is ranked ${result.rank}"
                ariaExpanded = expanded
                ariaControls = reasonId
                onClick = { expanded = !expanded }
                +"Why"
                Icon { name = IconName.Chevron }
            }
            div {
                id = reasonId
                className = ClassName("pb-result-reason")
                hidden = if (!expanded) web.html.Hidden.`true` else null
                +result.reason
            }
        }
    }
