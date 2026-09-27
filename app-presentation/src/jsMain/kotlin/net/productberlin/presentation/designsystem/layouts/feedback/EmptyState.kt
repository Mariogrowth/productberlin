package net.productberlin.presentation.designsystem.layouts.feedback

import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.text.Text
import net.productberlin.presentation.designsystem.components.text.TextStyle
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

external interface EmptyStateProps : Props {
    var title: String
    var description: String
    var actionLabel: String
    var onAction: () -> Unit
}

val EmptyState =
    FC<EmptyStateProps> { props ->
        div {
            className = ClassName("pb-empty-state")
            h3 {
                Text {
                    variant = TextStyle.Heading3
                    +props.title
                }
            }
            p { +props.description }
            Button {
                onClick = props.onAction
                +props.actionLabel
            }
        }
    }
