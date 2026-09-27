package net.productberlin.presentation.designsystem.components.text

import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface TextProps : PropsWithChildren {
    var variant: TextStyle?
    var weight: TextWeight?
    var muted: Boolean?
}

/** Visual typography; compose inside an appropriate heading, label or paragraph. */
val Text =
    FC<TextProps> { props ->
        span {
            className =
                ClassName(
                    "pb-text pb-text-${(props.variant ?: TextStyle.Body).name.lowercase()} " +
                        "pb-weight-${(props.weight ?: TextWeight.Regular).name.lowercase()}" +
                        "${if (props.muted == true) " pb-muted" else ""}",
                )
            +props.children
        }
    }
