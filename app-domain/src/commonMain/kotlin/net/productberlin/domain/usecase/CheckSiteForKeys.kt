package net.productberlin.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.repository.KeyScanRepository

/** Accepts what people paste (`https://myapp.com/page`, `MyApp.com`) and checks the site's bare hostname. */
class CheckSiteForKeys(
    private val repository: KeyScanRepository,
) {
    suspend operator fun invoke(input: String): KeyScanResult {
        val domain = hostname(input) ?: return KeyScanResult.InvalidDomain
        return try {
            repository.scan(domain)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            KeyScanResult.Unavailable
        }
    }

    companion object {
        /** A public DNS name with a letter TLD: no IP addresses, ports, credentials or `localhost`. */
        private val HOSTNAME = Regex("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")

        fun hostname(input: String): String? {
            val host =
                input
                    .trim()
                    .lowercase()
                    .substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('.')
            return host.takeIf { it.length <= 253 && HOSTNAME.matches(it) }
        }
    }
}
