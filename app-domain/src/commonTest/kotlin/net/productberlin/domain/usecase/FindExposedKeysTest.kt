package net.productberlin.domain.usecase

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.productberlin.domain.entity.KeyRisk
import net.productberlin.domain.entity.KeyType

/** Fake keys are assembled at runtime so no source file contains a credential-shaped literal. */
class FindExposedKeysTest {
    private val find = FindExposedKeys()

    private fun chars(
        count: Int,
        alphabet: String = "aB3dE5fG7hJ9kL2mN4pQ6rS8tU",
    ) = (0 until count).map { alphabet[(it * 7) % alphabet.length] }.joinToString("")

    private fun jwt(payload: String): String {
        val encode = { s: String -> Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(s.encodeToByteArray()) }
        return "${encode("""{"alg":"HS256","typ":"JWT"}""")}.${encode(payload)}.${chars(43)}"
    }

    private fun types(text: String) = find(text, "/app.js").map { it.type }

    @Test
    fun recognisesEachSecretFormatInScriptCode() {
        val samples =
            mapOf(
                KeyType.StripeSecret to "sk_" + "live_" + chars(24),
                KeyType.StripeRestricted to "rk_" + "live_" + chars(24),
                KeyType.OpenAi to "sk-" + "proj-" + chars(40),
                KeyType.Anthropic to "sk-" + "ant-api03-" + chars(40),
                KeyType.AwsAccessKey to "AKIA" + chars(16, "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"),
                KeyType.GitHubToken to "ghp" + "_" + chars(36),
                KeyType.SlackToken to "xox" + "b-" + chars(24),
                KeyType.SendGrid to "SG." + chars(22) + "." + chars(43),
                KeyType.Brevo to "xkey" + "sib-" + chars(64, "0123456789abcdef") + "-" + chars(16),
                KeyType.Mailgun to "key-" + chars(32, "0123456789abcdef"),
                KeyType.HuggingFace to "hf" + "_" + chars(34),
                KeyType.Groq to "gsk" + "_" + chars(52),
                KeyType.Replicate to "r8" + "_" + chars(37),
                KeyType.SupabaseSecret to "sb_" + "secret_" + chars(30),
                KeyType.MapboxSecret to "sk." + "eyJ" + chars(30) + "." + chars(20),
                KeyType.PrivateKey to "-----BEGIN RSA " + "PRIVATE KEY-----",
            )
        for ((type, key) in samples) {
            assertEquals(listOf(type), types("""const config = { key: "$key" };"""), type.name)
            assertEquals(KeyRisk.Secret, type.risk, type.name)
        }
    }

    @Test
    fun publicAndConditionalKeysAreToldApart() {
        assertEquals(listOf(KeyType.StripePublishable), types("Stripe('" + "pk_" + "live_" + chars(24) + "')"))
        assertEquals(listOf(KeyType.SupabasePublishable), types("sb_" + "publishable_" + chars(30)))
        assertEquals(listOf(KeyType.MapboxPublic), types("pk." + "eyJ" + chars(30) + "." + chars(20)))
        assertEquals(listOf(KeyType.GoogleApiKey), types("AIza" + chars(35)))
        assertEquals(KeyRisk.Depends, KeyType.GoogleApiKey.risk)
    }

    @Test
    fun supabaseLegacyKeysAreJudgedByTheRoleInsideTheToken() {
        assertEquals(listOf(KeyType.SupabaseAnon), types(jwt("""{"iss":"supabase","ref":"abc","role":"anon"}""")))
        assertEquals(listOf(KeyType.SupabaseServiceRole), types(jwt("""{"iss":"supabase","ref":"abc","role":"service_role"}""")))
        assertTrue(types(jwt("""{"sub":"user-1","role":"admin"}""")).isEmpty(), "other JWTs are not Supabase keys")
    }

    @Test
    fun onlyAMaskedHintAndTheLocationAreKept() {
        val key = "sk-" + "proj-" + chars(40)
        val finding = find("x=\"$key\"", "/assets/index-8f2a.js").single()
        assertEquals("sk-proj-…${key.takeLast(4)}", finding.masked)
        assertEquals("/assets/index-8f2a.js", finding.location)
        assertTrue(key !in finding.toString())
        assertEquals("-----BEGIN … PRIVATE KEY-----", FindExposedKeys.mask("-----BEGIN " + "PRIVATE KEY-----"))
    }

    @Test
    fun theSameKeyRepeatedIsReportedOnce() {
        val key = "sk_" + "live_" + chars(24)
        assertEquals(1, find("a='$key';b='$key'", "/app.js").size)
    }

    @Test
    fun ordinaryCodeAndLookalikesAreNotKeys() {
        val code =
            """
            const sk_live_mode = true; const keyName = "key-value"; const hash = "${chars(64, "0123456789abcdef")}";
            fetch("/api/rankings/weekly"); const brandfetch = "1idVlgkCIW8K520_bqV"; // risk-free public client id
            const task = "ask-ant-hill"; const sha = "sha256-${chars(43)}";
            """.trimIndent()
        assertTrue(types(code).isEmpty(), types(code).toString())
    }
}
