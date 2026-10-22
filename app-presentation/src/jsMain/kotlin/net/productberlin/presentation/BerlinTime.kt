package net.productberlin.presentation

import kotlin.js.Date

/** Event times are shown in Berlin time, whatever the reader's own time zone. */
internal object BerlinTime {
    /** "Wednesday 21 Oct". */
    fun day(iso: String): String = format(iso, js("({weekday:'long',month:'short',day:'numeric',timeZone:'Europe/Berlin'})"))

    /** "21". */
    fun dayOfMonth(iso: String): String = format(iso, js("({day:'numeric',timeZone:'Europe/Berlin'})"))

    /** "Wednesday · Oct". */
    fun weekdayAndMonth(iso: String): String =
        "${format(
            iso,
            js("({weekday:'long',timeZone:'Europe/Berlin'})"),
        )} · ${format(iso, js("({month:'short',timeZone:'Europe/Berlin'})"))}"

    /** "18:30". */
    fun time(iso: String): String = format(iso, js("({hour:'2-digit',minute:'2-digit',hourCycle:'h23',timeZone:'Europe/Berlin'})"))

    private fun format(
        iso: String,
        options: dynamic,
    ): String = Date(iso).asDynamic().toLocaleString("en-GB", options) as String
}
