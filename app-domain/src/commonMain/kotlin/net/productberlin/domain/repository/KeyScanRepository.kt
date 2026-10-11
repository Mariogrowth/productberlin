package net.productberlin.domain.repository

import net.productberlin.domain.entity.KeyScanResult

interface KeyScanRepository {
    /** Scans the public files of [domain], a validated bare hostname. Throws when the checker cannot be reached. */
    suspend fun scan(domain: String): KeyScanResult
}
