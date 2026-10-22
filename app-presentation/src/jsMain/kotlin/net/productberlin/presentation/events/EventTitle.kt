package net.productberlin.presentation.events

/**
 * An organiser's event title without what the section already says or what is decoration: a trailing "- Berlin",
 * ":: Berlin", "in Berlin" or "Berlin", a closing aside listing perks or tags ("(with free drinks courtesy of PostHog)",
 * "(Tech / AI / Data)"), and
 * trailing emoji or full stops. The start and middle of the title stay as written; a title that would become too
 * short stays whole.
 */
internal fun cleanEventTitle(title: String): String {
    var text = title.trim()
    repeat(2) {
        text = text.replace(TRAILING_SYMBOLS, "")
        text = text.replace(TRAILING_ASIDE) { match -> if (text.length - match.value.length >= MIN_LENGTH) "" else match.value }
        text = text.replace(TRAILING_BERLIN) { match -> if (text.length - match.value.length >= MIN_LENGTH) "" else match.value }
    }
    return text.ifBlank { title.trim() }
}

private const val MIN_LENGTH = 8
private val TRAILING_SYMBOLS = Regex("""[\s.\p{So}\p{Sk}‍️]+$""")

// Only perks and tag lists ("(with free drinks …)", "(Tech / AI / Data)"); "(Batch 4)" says something.
private val TRAILING_ASIDE =
    Regex(
        """\s*\((?:(?:with|mit|free|incl|inkl|hosted|powered|sponsored|presented)\b[^()]*|[^()]*\s/\s[^()]*)\)$""",
        RegexOption.IGNORE_CASE,
    )
private val TRAILING_BERLIN = Regex("""\s*(?:[-–—:|@·,]+\s*|\s+in\s+|\s+)Berlin$""", RegexOption.IGNORE_CASE)
