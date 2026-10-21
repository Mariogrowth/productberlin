package net.productberlin.presentation

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-12T…" → "Oct 12". */
internal fun shortDate(iso: String): String {
    val month = iso.substring(5, 7).toIntOrNull() ?: return iso.take(10)
    val day = iso.substring(8, 10).toIntOrNull() ?: return iso.take(10)
    return "${MONTHS.getOrElse(month - 1) { "" }} $day".trim()
}
