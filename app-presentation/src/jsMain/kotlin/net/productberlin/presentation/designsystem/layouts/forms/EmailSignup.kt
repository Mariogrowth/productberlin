package net.productberlin.presentation.designsystem.layouts.forms

import js.reflect.unsafeCast
import net.productberlin.presentation.designsystem.components.buttons.Button
import net.productberlin.presentation.designsystem.components.buttons.ButtonVariant
import react.FC
import react.Props
import react.dom.aria.AriaInvalid
import react.dom.aria.AriaLive
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.useId
import web.autofill.AutoFill
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.InputType
import web.html.email
import web.html.submit

external interface EmailSignupProps : Props {
    var title: String
    var value: String
    var onValueChange: (String) -> Unit
    var onSubmit: () -> Unit
    var status: EmailSignupStatus?
    var message: String?
    var placeholder: String?
    var actionLabel: String
    var submittingLabel: String?
    var note: String?
}

/**
 * A pill-shaped email field with an inline submit action. The title is the field's visible label. Status messages
 * are announced through a persistent live region; a successful submission replaces the field with its message.
 */
val EmailSignup =
    FC<EmailSignupProps> { props ->
        val fieldId = useId()
        val status = props.status ?: EmailSignupStatus.Idle
        val submitting = status == EmailSignupStatus.Submitting
        val message = props.message?.takeIf { it.isNotBlank() }
        val messageId = ElementId("$fieldId-message")
        div {
            className = ClassName("pb-email-signup")
            form {
                noValidate = true
                onSubmit = { event ->
                    event.preventDefault()
                    if (!submitting) props.onSubmit()
                }
                label {
                    htmlFor = fieldId
                    className = ClassName("pb-email-signup-title")
                    +props.title
                }
                if (status != EmailSignupStatus.Succeeded) {
                    div {
                        className = ClassName("pb-email-signup-pill")
                        input {
                            id = fieldId
                            type = InputType.email
                            name = "email"
                            autoComplete = unsafeCast<AutoFill>("email")
                            required = true
                            value = props.value
                            placeholder = props.placeholder
                            readOnly = submitting
                            ariaInvalid = if (status == EmailSignupStatus.Invalid) AriaInvalid.`true` else null
                            ariaDescribedBy = if (message != null) messageId else null
                            onChange = { props.onValueChange(it.target.value) }
                        }
                        Button {
                            variant = ButtonVariant.Primary
                            type = ButtonType.submit
                            disabled = submitting
                            +(if (submitting) props.submittingLabel ?: props.actionLabel else props.actionLabel)
                        }
                    }
                }
            }
            p {
                id = messageId
                ariaLive = AriaLive.polite
                className =
                    ClassName(
                        "pb-email-signup-message" +
                            when (status) {
                                EmailSignupStatus.Invalid, EmailSignupStatus.Failed -> " pb-error"
                                EmailSignupStatus.Succeeded -> " pb-success"
                                else -> ""
                            },
                    )
                if (message != null) +message
            }
            props.note?.let {
                if (status != EmailSignupStatus.Succeeded) {
                    p {
                        className = ClassName("pb-email-signup-note")
                        +it
                    }
                }
            }
        }
    }
