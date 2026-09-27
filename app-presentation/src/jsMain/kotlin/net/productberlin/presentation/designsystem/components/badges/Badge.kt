package net.productberlin.presentation.designsystem.components.badges

import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface BadgeProps : PropsWithChildren {
    var verified: Boolean?
}

/** Neutral tags or a success badge; meaning is always present in the visible children. */
val Badge =
    FC<BadgeProps> { props ->
        span {
            className = ClassName("pb-badge${if (props.verified == true) " pb-badge-verified" else ""}")
            if (props.verified == true) Icon { name = IconName.Verified }
            +props.children
        }
    }
