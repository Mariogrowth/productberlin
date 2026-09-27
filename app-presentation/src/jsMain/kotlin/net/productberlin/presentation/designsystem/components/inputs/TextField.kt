package net.productberlin.presentation.designsystem.components.inputs

import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonSize
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import net.productberlin.presentation.designsystem.components.icons.Icon
import net.productberlin.presentation.designsystem.components.icons.IconName
import react.FC
import react.Props
import react.dom.aria.AriaInvalid
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.useId
import web.cssom.ClassName
import web.dom.ElementId
import web.html.InputType
import web.html.search
import web.html.text

external interface TextFieldProps : Props {
    var label: String
    var value: String
    var onValueChange: (String) -> Unit
    var placeholder: String?
    var disabled: Boolean?
    var supportingText: String?
    var error: String?
    var actionLabel: String?
    var onAction: (() -> Unit)?
    var search: Boolean?
}

val TextField =
    FC<TextFieldProps> { props ->
        val fieldId = useId()
        val message = props.error ?: props.supportingText
        div {
            className = ClassName("pb-field${if (props.search == true) " pb-search" else ""}")
            label {
                htmlFor = fieldId
                +props.label
            }
            div {
                className = ClassName("pb-input-wrap")
                input {
                    id = fieldId
                    type = if (props.search == true) InputType.search else InputType.text
                    value = props.value
                    placeholder = props.placeholder
                    disabled = props.disabled == true
                    ariaInvalid = if (props.error != null) AriaInvalid.`true` else null
                    ariaDescribedBy = if (message != null) ElementId("$fieldId-message") else null
                    onChange = { props.onValueChange(it.target.value) }
                }
                if (props.onAction != null) {
                    require(!props.actionLabel.isNullOrBlank()) { "An input action needs an accessible label" }
                    Button {
                        variant = ButtonVariant.Tertiary
                        size = ButtonSize.Small
                        accessibleLabel = props.actionLabel
                        disabled = props.disabled
                        onClick = props.onAction
                        Icon { name = if (props.search == true) IconName.Search else IconName.Help }
                    }
                } else if (props.search == true) {
                    Icon { name = IconName.Search }
                }
            }
            if (message != null) {
                div {
                    id = ElementId("$fieldId-message")
                    className =
                        ClassName("pb-field-message${if (props.error != null) " pb-error" else ""}")
                    +message
                }
            }
        }
    }
