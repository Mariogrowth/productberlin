package net.productberlin.worker.logo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrandfetchLogoTest {
    @Test
    fun buildsSizedIconUrlWithNotFoundFallbackAndClientId() {
        assertEquals(
            "https://cdn.brandfetch.io/domain/n26.com/w/80/h/80/fallback/404/type/icon?c=client_ID-1",
            BrandfetchLogo("client_ID-1").url("n26.com"),
        )
    }

    @Test
    fun omitsLogoWithoutDomainOrUsableClientId() {
        assertNull(BrandfetchLogo("client").url(null))
        for (clientId in listOf(null, "", " ", "a&b=c", "a/b")) {
            assertNull(BrandfetchLogo(clientId).url("n26.com"))
        }
    }
}
