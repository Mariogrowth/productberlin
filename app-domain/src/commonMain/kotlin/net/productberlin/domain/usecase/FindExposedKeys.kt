package net.productberlin.domain.usecase

import kotlin.io.encoding.Base64
import net.productberlin.domain.entity.KeyFinding
import net.productberlin.domain.entity.KeyType

/**
 * Finds credentials in public text (HTML, JavaScript, source maps) by their documented formats. Matching is by
 * format only: a found key is never used or tested. Only a masked hint is returned: the service prefix and the last
 * four characters.
 */
class FindExposedKeys {
    operator fun invoke(
        text: String,
        location: String,
    ): List<KeyFinding> {
        val findings = mutableListOf<KeyFinding>()
        for ((type, pattern) in PATTERNS) {
            for (match in pattern.findAll(text)) findings += KeyFinding(type, mask(match.value), location)
        }
        for (match in JWT.findAll(text)) {
            val type = supabaseRole(match.value) ?: continue
            findings += KeyFinding(type, mask(match.value), location)
        }
        return findings.distinctBy { it.type to it.masked }
    }

    /** Supabase's legacy keys are JWTs; the role inside tells the public anon key from the service-role key. */
    private fun supabaseRole(token: String): KeyType? {
        val payload =
            try {
                Base64.UrlSafe
                    .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                    .decode(token.split('.')[1])
                    .decodeToString()
            } catch (invalid: IllegalArgumentException) {
                return null
            }
        if (!payload.contains("supabase")) return null
        return when {
            payload.contains("\"role\":\"service_role\"") -> KeyType.SupabaseServiceRole
            payload.contains("\"role\":\"anon\"") -> KeyType.SupabaseAnon
            else -> null
        }
    }

    companion object {
        private val JWT = Regex("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")

        /** Longest, most specific formats first; later overlapping matches of the same text are still distinct types. */
        private val PATTERNS =
            listOf(
                KeyType.StripeSecret to Regex("\\bsk_(live|test)_[0-9A-Za-z]{20,}"),
                KeyType.StripeRestricted to Regex("\\brk_(live|test)_[0-9A-Za-z]{20,}"),
                KeyType.StripePublishable to Regex("\\bpk_(live|test)_[0-9A-Za-z]{20,}"),
                KeyType.Anthropic to Regex("\\bsk-ant-[a-z]+[0-9]{2}-[A-Za-z0-9_-]{20,}"),
                KeyType.OpenAi to Regex("\\bsk-(proj|svcacct|admin)-[A-Za-z0-9_-]{20,}|\\bsk-[A-Za-z0-9]{20}T3BlbkFJ[A-Za-z0-9]{20}"),
                KeyType.AwsAccessKey to Regex("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
                KeyType.GoogleApiKey to Regex("\\bAIza[0-9A-Za-z_-]{35}"),
                KeyType.GitHubToken to Regex("\\b(gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{60,})"),
                KeyType.SlackToken to Regex("\\bxox[baprs]-[0-9A-Za-z-]{10,}"),
                KeyType.SendGrid to Regex("\\bSG\\.[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}"),
                KeyType.Brevo to Regex("\\bxkeysib-[a-f0-9]{64}-[A-Za-z0-9]{16}"),
                KeyType.Mailgun to Regex("\\bkey-[a-f0-9]{32}\\b"),
                KeyType.HuggingFace to Regex("\\bhf_[A-Za-z0-9]{34}\\b"),
                KeyType.Groq to Regex("\\bgsk_[A-Za-z0-9]{52}\\b"),
                KeyType.Replicate to Regex("\\br8_[A-Za-z0-9]{37}\\b"),
                KeyType.SupabaseSecret to Regex("\\bsb_secret_[A-Za-z0-9_-]{20,}"),
                KeyType.SupabasePublishable to Regex("\\bsb_publishable_[A-Za-z0-9_-]{20,}"),
                KeyType.MapboxSecret to Regex("\\bsk\\.eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}"),
                KeyType.MapboxPublic to Regex("\\bpk\\.eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,}"),
                KeyType.PrivateKey to Regex("-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
            )

        /** The recognisable prefix (up to and including its last separator, at most 12 characters) and the last four. */
        fun mask(value: String): String {
            if (value.startsWith("-----BEGIN")) return "-----BEGIN … PRIVATE KEY-----"
            val separator = value.take(12).indexOfLast { it == '_' || it == '-' || it == '.' }
            val prefix = if (separator >= 1) value.take(separator + 1) else value.take(4)
            return "$prefix…${value.takeLast(4)}"
        }
    }
}
