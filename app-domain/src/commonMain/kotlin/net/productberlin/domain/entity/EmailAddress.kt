package net.productberlin.domain.entity

import kotlin.jvm.JvmInline

/**
 * A trimmed, syntactically plausible email address. This only rejects obvious typos; double opt-in proves that the
 * address exists and belongs to the person subscribing.
 */
@JvmInline
value class EmailAddress private constructor(
    val value: String,
) {
    companion object {
        private const val MAX_LENGTH = 254
        private val PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun parse(input: String): EmailAddress? =
            input.trim().takeIf { it.length <= MAX_LENGTH && PATTERN.matches(it) }?.let(::EmailAddress)
    }
}
