package net.productberlin.presentation.designsystem.components.buttons

import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import web.cssom.ClassName
import web.html.ButtonType
import web.html.button

external interface ButtonProps : PropsWithChildren {
    var variant: ButtonVariant?
    var size: ButtonSize?
    var disabled: Boolean?
    var accessibleLabel: String?
    var type: ButtonType?
    var onClick: (() -> Unit)?
}

val Button =
    FC<ButtonProps> { props ->
        button {
            className =
                ClassName(
                    "pb-button pb-button-${(props.variant ?: ButtonVariant.Primary).name.lowercase()} " +
                        "pb-button-${(props.size ?: ButtonSize.Regular).name.lowercase()}",
                )
            type = props.type ?: ButtonType.button
            disabled = props.disabled == true
            ariaLabel = props.accessibleLabel
            onClick = { props.onClick?.invoke() }
            +props.children
        }
    }
