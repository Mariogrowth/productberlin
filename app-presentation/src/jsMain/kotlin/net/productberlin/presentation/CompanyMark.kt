package net.productberlin.presentation

import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.useState
import web.cssom.ClassName
import web.http.ReferrerPolicy
import web.http.strictOriginWhenCrossOrigin

external interface CompanyMarkProps : Props {
    var companyId: String
    var companyName: String
    var logoUrl: String?
    var logoFallbackUrl: String?

    /** Extra class for the context, e.g. a smaller mark in the news list. */
    var variant: String?
}

/**
 * A company's decorative mark: the square icon first, then the full logo, then a letter mark; each failed URL is
 * skipped. Only absolute HTTPS image URLs supplied by the API are rendered. Hidden from assistive technology because
 * the company name always follows.
 */
val CompanyMark =
    FC<CompanyMarkProps> { props ->
        var failedLogos by useState(emptySet<String>())
        val logo =
            listOfNotNull(props.logoUrl, props.logoFallbackUrl)
                .firstOrNull { it !in failedLogos && SAFE_LOGO.matches(it) }
        val variant = props.variant?.let { " $it" } ?: ""
        div {
            className = ClassName(if (logo != null) "company-mark has-logo$variant" else "company-mark mark-${props.companyId}$variant")
            ariaHidden = true
            if (logo != null) {
                img {
                    src = logo
                    alt = ""
                    width = 40.0
                    height = 40.0
                    referrerPolicy = ReferrerPolicy.strictOriginWhenCrossOrigin
                    onError = { failedLogos = failedLogos + logo }
                }
            } else {
                +letterMark(props.companyId, props.companyName)
            }
        }
    }

private val SAFE_LOGO = Regex("^https://[^\\s/<>]+/[^\\s<>\"]*$")

/** The short text shown in a company's mark when no logo is available. */
internal fun letterMark(
    id: String,
    name: String,
): String =
    when (id) {
        "almedia" -> "a"
        "aignostics" -> "ai"
        "n26" -> "N26"
        "parloa" -> "P"
        "sennder" -> "s"
        "enpal" -> "en"
        "trade-republic" -> "TR"
        "deepset" -> "d"
        "personio" -> "p"
        else -> name.take(2)
    }
