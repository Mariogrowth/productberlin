package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.KeyScan
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.repository.KeyScanRepository

class CheckSiteForKeysTest {
    private class Recording(
        private val result: () -> KeyScanResult,
    ) : KeyScanRepository {
        val scanned = mutableListOf<String>()

        override suspend fun scan(domain: String): KeyScanResult {
            scanned += domain
            return result()
        }
    }

    @Test
    fun pastedAddressesBecomeTheBareHostname() =
        runTest {
            val repository = Recording { KeyScanResult.Completed(KeyScan("myapp.com", 1, 0, emptyList())) }
            for (input in listOf("myapp.com", " MyApp.com ", "https://myapp.com/pricing?x=1", "http://myapp.com#top", "myapp.com.")) {
                CheckSiteForKeys(repository)(input)
            }
            assertEquals(List(5) { "myapp.com" }, repository.scanned)
        }

    @Test
    fun anythingButAPublicHostnameIsRejectedWithoutARequest() =
        runTest {
            val repository = Recording { error("must not scan") }
            for (input in listOf("", "localhost", "127.0.0.1", "myapp.com:8080", "user@myapp.com", "my app.com", "myapp", "-bad-.com")) {
                assertEquals(KeyScanResult.InvalidDomain, CheckSiteForKeys(repository)(input), input)
            }
            assertEquals(emptyList(), repository.scanned)
        }

    @Test
    fun checkerFailuresBecomeARetryableResult() =
        runTest {
            assertEquals(KeyScanResult.Unavailable, CheckSiteForKeys(Recording { error("offline") })("myapp.com"))
        }
}
