package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedPublishersTest {
    private val trusted = TrustedPublishers(listOf("handelsblatt.com", "deutsche-startups.de", "xpert.digital"))

    @Test
    fun allowsListedDomainsAndTheirSubdomains() {
        for (url in listOf(
            "https://www.handelsblatt.com",
            "https://live.handelsblatt.com/x",
            "HTTPS://DEUTSCHE-STARTUPS.DE/",
            "https://xpert.digital",
            "https://handelsblatt.com.",
        )) {
            assertTrue(trusted.allows("Publisher", url), url)
        }
    }

    @Test
    fun rejectsUnlistedLookalikeVideoAndUnreadableSources() {
        for (url in listOf(
            "https://popsci.com",
            "https://evilhandelsblatt.com",
            "https://handelsblatt.com.evil.net",
            "https://www.youtube.com",
            null,
            "",
            "not a url",
            "ftp://handelsblatt.com",
        )) {
            assertFalse(trusted.allows("Handelsblatt", url), url.toString())
        }
    }

    @Test
    fun parsesTheEditorialListAndRejectsInvalidEntries() {
        val valid = """[{"domain":"handelsblatt.com","name":"Handelsblatt","type":"business"}]"""
        assertTrue(parsePublishers(valid).allows("Handelsblatt", "https://www.handelsblatt.com"))
        for (json in listOf(
            "[]",
            """[{"domain":"https://handelsblatt.com","name":"H","type":"business"}]""",
            """[{"domain":"handelsblatt.com","name":"H","type":"blog"}]""",
            """[{"domain":"handelsblatt.com","name":" ","type":"business"}]""",
            """[{"domain":"handelsblatt.com","type":"business"}]""",
            "[" + List(2) { """{"domain":"handelsblatt.com","name":"H","type":"business"}""" }.joinToString(",") + "]",
        )) {
            assertFailsWith<IllegalArgumentException>(json) { parsePublishers(json) }
        }
    }
}
