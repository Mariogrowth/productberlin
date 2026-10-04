package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublisherPolicyTest {
    @Test
    fun allowsEuropeanSwissNorthAmericanBritishAustralianAndGenericPublishers() {
        for (url in listOf(
            "https://www.handelsblatt.de",
            "https://brutkasten.com",
            "https://www.derstandard.at",
            "https://www.lemonde.fr",
            "https://www.nzz.ch",
            "https://sifted.eu",
            "https://www.bbc.co.uk",
            "https://www.theglobeandmail.ca",
            "https://www.abc.net.au",
            "https://www.nytimes.com",
            "https://news.example.us",
            "https://techcrunch.com",
            "https://tech.eu",
            "https://example.org",
            "https://the.news",
            "https://grid.gg",
            "https://startup.io",
            "https://deepset.ai",
            "https://example.co",
            "HTTPS://EXAMPLE.DE/path?x=1",
            "https://example.de.",
        )) {
            assertTrue(PublisherPolicy.allows("Publisher", url), url)
        }
    }

    @Test
    fun blocksPublishersOnOtherCountryDomains() {
        for (url in listOf(
            "https://politiko.com.ph",
            "https://www.timesofindia.in",
            "https://example.com.br",
            "https://example.no",
            "https://example.jp",
            "https://example.cn",
            "https://example.ru",
            "https://example.ng",
        )) {
            assertFalse(PublisherPolicy.allows("Publisher", url), url)
        }
    }

    @Test
    fun blocksYouTubeByNameOrSite() {
        assertFalse(PublisherPolicy.allows("YouTube", null))
        assertFalse(PublisherPolicy.allows(" youtube ", "https://example.com"))
        assertFalse(PublisherPolicy.allows("Some channel", "https://www.youtube.com"))
        assertFalse(PublisherPolicy.allows("Some channel", "https://m.youtube.com/watch"))
        assertFalse(PublisherPolicy.allows("Some channel", "https://youtu.be/abc"))
        assertTrue(PublisherPolicy.allows("Not YouTube", "https://notyoutube.com"))
    }

    @Test
    fun publishersWithoutAReadableSiteAreKept() {
        for (url in listOf(null, "", "not a url", "ftp://example.ph")) {
            assertTrue(PublisherPolicy.allows("Publisher", url), url.toString())
        }
    }
}
