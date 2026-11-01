package net.productberlin.presentation

import js.reflect.unsafeCast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.usecase.SubscribeToNewsletter
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignup
import net.productberlin.presentation.designsystem.layouts.forms.EmailSignupStatus
import react.FC
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.useEffect
import react.useEffectOnce
import react.useState
import web.autofill.AutoFill
import web.cssom.ClassName
import web.dom.ElementId
import web.dom.document
import web.html.ButtonType
import web.html.InputType
import web.html.button
import web.html.text
import web.window.window

external interface NewsletterSignupProps : Props {
    var subscribe: SubscribeToNewsletter
}

private data class Submission(
    val email: String,
    val attempt: Int,
)

/**
 * Weekly-list sign-up, docked to the bottom of the screen: it slides up once the reader scrolls past the top of the
 * page, or when the header's "Newsletter" link is followed, and stays while they read. Closing it hides it until that
 * link is followed again. Every outcome is reported beneath the pill.
 */
val NewsletterSignup =
    FC<NewsletterSignupProps> { props ->
        var email by useState("")
        var spamTrap by useState("")
        var attempts by useState(0)
        var submission by useState<Submission?>(null)
        var status by useState(EmailSignupStatus.Idle)
        var message by useState<String?>(null)
        var shown by useState(false)
        var closed by useState(false)

        useEffectOnce {
            val reveal = { if (window.scrollY > SHOW_AFTER_SCROLL_PX) shown = true }
            val openFromLink = {
                if (window.location.hash == "#newsletter") {
                    closed = false
                    shown = true
                    // Once it is visible: a hidden field cannot take focus.
                    window.asDynamic().setTimeout({
                        document.querySelector("#newsletter input[type=email]")?.asDynamic()?.focus(js("({preventScroll:true})"))
                    }, FOCUS_DELAY_MS)
                }
            }
            val target = window.asDynamic()
            target.addEventListener("scroll", reveal, js("({passive:true})"))
            target.addEventListener("hashchange", openFromLink)
            reveal()
            openFromLink()
            try {
                awaitCancellation()
            } finally {
                target.removeEventListener("scroll", reveal)
                target.removeEventListener("hashchange", openFromLink)
            }
        }

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
            id = ElementId("newsletter")
            className = ClassName("pb-theme newsletter newsletter-bar" + if (shown && !closed) " is-shown" else "")
            role = AriaRole.region
            ariaLabel = "Newsletter sign-up"
            button {
                type = ButtonType.button
                className = ClassName("newsletter-close")
                ariaLabel = "Close newsletter sign-up"
                onClick = {
                    closed = true
                    if (window.location.hash == "#newsletter") window.asDynamic().history.replaceState(null, "", window.location.pathname)
                }
                +"×"
            }
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

private fun SubscriptionResult.message() =
    when (this) {
        SubscriptionResult.ConfirmationSent -> "Verify you are human, check your inbox"
        SubscriptionResult.InvalidEmail -> "Enter a valid email address."
        SubscriptionResult.Unavailable -> "Something went wrong. Please try again."
    }

/** How far the reader scrolls before the bar slides up: past the header and the top of the lead. */
private const val SHOW_AFTER_SCROLL_PX = 320
private const val FOCUS_DELAY_MS = 50
