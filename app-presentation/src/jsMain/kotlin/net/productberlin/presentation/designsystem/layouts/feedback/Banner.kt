package net.productberlin.presentation.designsystem.layouts.feedback

import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonSize
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

external interface BannerProps : Props {
    var message: String
    var onDismiss: () -> Unit
    var dismissLabel: String?
}

/**
 * A neutral confirmation notice that sits in the page flow. It is announced politely and stays until the visitor
 * dismisses it; the caller owns whether it is shown.
 */
val Banner =
    FC<BannerProps> { props ->
        div {
            className = ClassName("pb-banner")
            role = AriaRole.status
            Icon { name = IconName.Check }
            p {
                className = ClassName("pb-banner-message")
                +props.message
            }
            Button {
                variant = ButtonVariant.Tertiary
                size = ButtonSize.Small
                accessibleLabel = props.dismissLabel ?: "Dismiss"
                onClick = props.onDismiss
                Icon { name = IconName.Close }
            }
        }
    }
