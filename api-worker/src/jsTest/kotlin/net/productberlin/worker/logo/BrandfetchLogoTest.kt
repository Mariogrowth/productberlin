package net.productberlin.worker.logo

import kotlin.test.Test
import kotlin.test.assertEquals

class BrandfetchLogoTest {
    @Test
    fun buildsSizedIconAndFullLogoUrlsWithNotFoundFallbackAndClientId() {
        assertEquals(
            CompanyLogos(
                "https://cdn.brandfetch.io/domain/helsing.ai/w/80/h/80/fallback/404/type/icon?c=client_ID-1",
                "https://cdn.brandfetch.io/domain/helsing.ai/w/80/h/80/fallback/404/type/logo?c=client_ID-1",
            ),
            BrandfetchLogo("client_ID-1").urls("helsing.ai"),
        )
    }

    @Test
    fun omitsLogosWithoutDomainOrUsableClientId() {
        assertEquals(CompanyLogos(), BrandfetchLogo("client").urls(null))
        for (clientId in listOf(null, "", " ", "a&b=c", "a/b")) {
            assertEquals(CompanyLogos(), BrandfetchLogo(clientId).urls("n26.com"))
        }
    }
}
