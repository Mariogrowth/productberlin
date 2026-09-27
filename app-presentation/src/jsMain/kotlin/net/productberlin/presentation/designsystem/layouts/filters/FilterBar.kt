package net.productberlin.presentation.designsystem.layouts.filters

import react.FC
import react.Key
import react.Props
import react.dom.aria.AriaPressed
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import web.cssom.ClassName
import web.html.ButtonType
import web.html.button

external interface FilterBarProps : Props {
    var label: String
    var options: List<FilterOption>
    var selectedId: String
    var onSelect: (String) -> Unit
}

val FilterBar =
    FC<FilterBarProps> { props ->
        div {
            className = ClassName("pb-filters")
            role = AriaRole.group
            ariaLabel = props.label
            props.options.forEach { option ->
                button {
                    key = Key(option.id)
                    type = ButtonType.button
                    className = ClassName("pb-filter")
                    ariaPressed = if (option.id == props.selectedId) AriaPressed.`true` else AriaPressed.`false`
                    onClick = { props.onSelect(option.id) }
                    +option.label
                }
            }
        }
    }
