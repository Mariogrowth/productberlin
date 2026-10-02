package net.productberlin.presentation

import net.productberlin.presentation.designsystem.layouts.forms.EmailSignup
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignupStatus
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState
import web.cssom.ClassName

/**
 * Weekly-list sign-up below the ranking. Delivery is not connected yet: a valid address is not sent anywhere, and
 * the visitor is told so instead of being shown a false confirmation.
 */
val NewsletterSignup =
    FC<Props> {
        var email by useState("")
        var status by useState(EmailSignupStatus.Idle)
        var message by useState<String?>(null)
        div {
            className = ClassName("pb-theme newsletter")
            EmailSignup {
                title = "Get next Monday’s ten in your inbox"
                value = email
                placeholder = "you@company.com"
                actionLabel = "Notify me"
                submittingLabel = "Sending…"
                note = "One email a week. Unsubscribe anytime."
                this.status = status
                this.message = message
                onValueChange = {
                    email = it
                    if (status == EmailSignupStatus.Invalid) {
                        status = EmailSignupStatus.Idle
                        message = null
                    }
                }
                onSubmit = {
                    if (EMAIL.matches(email.trim())) {
                        status = EmailSignupStatus.Idle
                        message = "Sign-ups aren’t open yet. Check back soon."
                    } else {
                        status = EmailSignupStatus.Invalid
                        message = "Enter a valid email address."
                    }
                }
            }
        }
    }

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
