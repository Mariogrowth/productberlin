package net.productberlin.presentation

import js.reflect.unsafeCast
import kotlinx.coroutines.CancellationException
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyRisk
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.usecase.CheckSiteForKeys
import net.productberlin.presentation.keycheck.advice
import net.productberlin.presentation.keycheck.claudePrompt
import net.productberlin.presentation.keycheck.title
import react.FC
import react.Key
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.code
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import web.autofill.AutoFill
import web.cssom.ClassName
import web.dom.ElementId
import web.html.ButtonType
import web.html.InputType
import web.html.button
import web.html.submit
import web.html.text
import web.navigator.navigator

external interface KeyCheckPageProps : Props {
    var checkSite: CheckSiteForKeys
}

private sealed interface CheckState {
    data object Idle : CheckState

    data object Checking : CheckState

    data class Done(
        val result: KeyScanResult,
    ) : CheckState
}

/** Prototype: paste a domain, see which API keys the site exposes to every visitor and how to fix them. */
val KeyCheckPage =
    FC<KeyCheckPageProps> { props ->
        var input by useState("")
        var request by useState<Pair<String, Int>?>(null)
        var state by useState<CheckState>(CheckState.Idle)
        useEffect(request) {
            val (domain, _) = request ?: return@useEffect
            state = CheckState.Checking
            state =
                try {
                    CheckState.Done(props.checkSite(domain))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    CheckState.Done(KeyScanResult.Unavailable)
                }
        }

        main {
            className = ClassName("key-check")
            a {
                className = ClassName("privacy-home")
                href = "/"
                +"← Product.berlin"
            }
            h1 { +"Is your site leaking API keys?" }
            p {
                className = ClassName("key-check-intro")
                +(
                    "Paste your domain. We read only what your site sends to every visitor (the page, its scripts and " +
                        "source maps) and look for API keys. We never use or store a key: you only see a masked hint."
                )
            }
            form {
                className = ClassName("key-check-form")
                onSubmit = { event ->
                    event.preventDefault()
                    request = input to ((request?.second ?: 0) + 1)
                }
                label {
                    htmlFor = ElementId("key-check-domain")
                    +"Your domain"
                }
                div {
                    className = ClassName("key-check-row")
                    input {
                        id = ElementId("key-check-domain")
                        type = InputType.text
                        placeholder = "myapp.com"
                        autoComplete = unsafeCast<AutoFill>("off")
                        spellCheck = false
                        value = input
                        onChange = { input = it.target.value }
                    }
                    button {
                        type = ButtonType.submit
                        disabled = state == CheckState.Checking || input.isBlank()
                        +(if (state == CheckState.Checking) "Checking…" else "Check my site")
                    }
                }
            }
            when (val current = state) {
                CheckState.Idle -> {
                    Unit
                }

                CheckState.Checking -> {
                    p {
                        className = ClassName("key-check-status")
                        role = AriaRole.status
                        +"Reading your site’s public files…"
                    }
                }

                is CheckState.Done -> {
                    when (val result = current.result) {
                        is KeyScanResult.Completed -> {
                            Report { scan = result.scan }
                        }

                        KeyScanResult.InvalidDomain -> {
                            Problem { message = "That doesn’t look like a domain. Try something like myapp.com." }
                        }

                        KeyScanResult.Unreachable -> {
                            Problem {
                                message =
                                    "We couldn’t reach that site over HTTPS. Check the domain and try again."
                            }
                        }

                        KeyScanResult.RateLimited -> {
                            Problem { message = "One check per minute, please. Try again in a minute." }
                        }

                        KeyScanResult.Unavailable -> {
                            Problem { message = "The checker is unavailable right now. Please try again." }
                        }
                    }
                }
            }
            p {
                className = ClassName("key-check-note")
                +(
                    "Prototype. It only sees public files, not your server, database or code repository, so a clean " +
                        "result means nothing is exposed to visitors, not that every key is safe."
                )
            }
        }
    }

private external interface ProblemProps : Props {
    var message: String
}

private val Problem =
    FC<ProblemProps> { props ->
        p {
            className = ClassName("key-check-status")
            role = AriaRole.alert
            +props.message
        }
    }

private external interface ReportProps : Props {
    var scan: KeyScan
}

private val Report =
    FC<ReportProps> { props ->
        val scan = props.scan
        val urgent = scan.secrets.size
        section {
            className = ClassName("key-check-report")
            ariaLabelledBy = ElementId("key-check-summary")
            h2 {
                id = ElementId("key-check-summary")
                +(
                    when {
                        urgent == 1 -> "${scan.domain}: 1 urgent problem"
                        urgent > 1 -> "${scan.domain}: $urgent urgent problems"
                        scan.findings.isEmpty() -> "${scan.domain}: no API keys found"
                        else -> "${scan.domain}: no secret keys found"
                    }
                )
            }
            p {
                className = ClassName("key-check-meta")
                +"${scan.filesScanned} public ${if (scan.filesScanned == 1) "file" else "files"} checked"
                if (scan.sourceMaps > 0) +" · ${scan.sourceMaps} source ${if (scan.sourceMaps == 1) "map" else "maps"}"
            }
            if (scan.sourceMaps > 0) {
                p {
                    className = ClassName("key-check-aside")
                    +"Your source maps are public, so anyone can read your original code. Consider not publishing them."
                }
            }
            val ordered = scan.findings.sortedBy { listOf(KeyRisk.Secret, KeyRisk.Depends, KeyRisk.Public).indexOf(it.type.risk) }
            for (finding in ordered) {
                Finding {
                    key = Key("${finding.type}:${finding.masked}")
                    this.finding = finding
                    domain = scan.domain
                }
            }
        }
    }

private external interface FindingProps : Props {
    var finding: KeyFinding
    var domain: String
}

private val Finding =
    FC<FindingProps> { props ->
        val finding = props.finding
        val advice = finding.advice()
        var copied by useState(false)
        article {
            className = ClassName("key-finding risk-${finding.type.risk.name.lowercase()}")
            span {
                className = ClassName("key-finding-verdict")
                +advice.verdict
            }
            h3 { +finding.type.title() }
            p {
                className = ClassName("key-finding-where")
                code { +finding.masked }
                +" in "
                code { +finding.location }
            }
            p { +advice.meaning }
            advice.fix?.let { p { +"What to do: $it" } }
            if (finding.type.risk == KeyRisk.Secret) {
                button {
                    type = ButtonType.button
                    className = ClassName("key-finding-copy")
                    onClick = { navigator.clipboard.writeTextAsync(finding.claudePrompt(props.domain)).then { copied = true } }
                    +(if (copied) "Prompt copied" else "Copy a prompt to fix it with Claude")
                }
            }
        }
    }
