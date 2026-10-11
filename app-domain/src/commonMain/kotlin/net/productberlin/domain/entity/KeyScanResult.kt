package net.productberlin.domain.entity

sealed interface KeyScanResult {
    data class Completed(
        val scan: KeyScan,
    ) : KeyScanResult

    /** Not a public hostname such as `myapp.com`. */
    data object InvalidDomain : KeyScanResult

    /** The site did not answer over HTTPS. */
    data object Unreachable : KeyScanResult

    /** The checker itself failed; worth retrying. */
    data object Unavailable : KeyScanResult
}
