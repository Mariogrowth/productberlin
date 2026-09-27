package net.productberlin.presentation.designsystem.components.toggles

import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import web.html.InputType
import web.html.checkbox

external interface CheckboxProps : Props {
    var label: String
    var checked: Boolean
    var onCheckedChange: (Boolean) -> Unit
    var disabled: Boolean?
    var hideLabel: Boolean?
    var name: String?
    var value: String?
}

val Checkbox =
    FC<CheckboxProps> { props ->
        label {
            className = ClassName("pb-control pb-control-checkbox")
            input {
                type = InputType.checkbox

                checked = props.checked
                disabled = props.disabled == true
                name = props.name
                value = props.value
                onChange = { props.onCheckedChange(it.target.checked) }
            }
            span {
                className = ClassName("pb-control-mark")
                ariaHidden = true
                Icon { name = IconName.Check }
            }
            span {
                className = if (props.hideLabel == true) ClassName("pb-sr-only") else null
                +props.label
            }
        }
    }
