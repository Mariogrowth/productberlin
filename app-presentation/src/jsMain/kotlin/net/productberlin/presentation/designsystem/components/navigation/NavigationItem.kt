package net.productberlin.presentation.designsystem.components.navigation

import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.Props
import react.dom.aria.AriaCurrent
import react.dom.html.ReactHTML.a
import web.cssom.ClassName

external interface NavigationItemProps : Props {
    var label: String
    var href: String
    var active: Boolean?
    var icon: IconName?
}

val NavigationItem =
    FC<NavigationItemProps> { props ->
        a {
            className = ClassName("pb-navigation-item")
            href = props.href
            ariaCurrent = if (props.active == true) AriaCurrent.page else null
            Icon { name = props.icon ?: IconName.Grid }
            +props.label
        }
    }
