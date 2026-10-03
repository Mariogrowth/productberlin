package net.productberlin.presentation

import js.reflect.unsafeCast
import kotlinx.coroutines.CancellationException
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.usecase.SubscribeToNewsletter
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignup
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignupStatus
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.useEffect
import react.useState
import web.autofill.AutoFill
import web.cssom.ClassName
import web.html.InputType
import web.html.text

external interface NewsletterSignupProps : Props {
    var subscribe: SubscribeToNewsletter

    /** The visitor has just followed the double opt-in link from the confirmation email. */
    var confirmed: Boolean?
}

private data class Submission(
    val email: String,
    val attempt: Int,
)

/** Weekly-list sign-up below the ranking. Every outcome is reported beneath the pill, which stays in place. */
val NewsletterSignup =
    FC<NewsletterSignupProps> { props ->
        var email by useState("")
        var spamTrap by useState("")
        var attempts by useState(0)
        var submission by useState<Submission?>(null)
        var status by useState(if (props.confirmed == true) EmailSignupStatus.Succeeded else EmailSignupStatus.Idle)
        var message by useState(if (props.confirmed == true) CONFIRMED else null)

        useEffect(submission) {
            val current = submission ?: return@useEffect
            status = EmailSignupStatus.Submitting
            message = null
            val result =
                try {
                    props.subscribe(current.email)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SubscriptionResult.Unavailable
                }
            status =
                when (result) {
                    SubscriptionResult.ConfirmationSent -> EmailSignupStatus.Succeeded
                    SubscriptionResult.InvalidEmail -> EmailSignupStatus.Invalid
                    SubscriptionResult.Unavailable -> EmailSignupStatus.Failed
                }
            message = result.message()
        }

        div {
            className = ClassName("pb-theme newsletter")
            EmailSignup {
                title = "One email a week. Unsubscribe anytime."
                value = email
                placeholder = "you@company.com"
                actionLabel = "Notify me"
                submittingLabel = "Sending…"
                privacyHref = "/privacy"
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
                    if (spamTrap.isNotEmpty()) {
                        // Automated form fillers get the normal reply, but nothing is sent.
                        status = EmailSignupStatus.Succeeded
                        message = SubscriptionResult.ConfirmationSent.message()
                    } else {
                        attempts += 1
                        submission = Submission(email, attempts)
                    }
                }
            }
            // Spam trap: hidden from people and assistive technology, but present for bots that fill every field.
            input {
                className = ClassName("newsletter-trap")
                type = InputType.text
                name = "website"
                tabIndex = -1
                ariaHidden = true
                autoComplete = unsafeCast<AutoFill>("off")
                value = spamTrap
                onChange = { spamTrap = it.target.value }
            }
        }
    }

private const val CONFIRMED = "You’re in. Welcome to Berlin’s builder community."

private fun SubscriptionResult.message() =
    when (this) {
        SubscriptionResult.ConfirmationSent -> "Verify you are human, check your inbox"
        SubscriptionResult.InvalidEmail -> "Enter a valid email address."
        SubscriptionResult.Unavailable -> "Something went wrong. Please try again."
    }
