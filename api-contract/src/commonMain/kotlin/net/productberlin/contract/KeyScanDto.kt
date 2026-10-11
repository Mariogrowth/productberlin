package net.productberlin.contract

import kotlinx.serialization.Serializable

/** `GET /api/key-check?domain=…` response. */
@Serializable
data class KeyScanDto(
    val domain: String,
    val filesScanned: Int,
    val sourceMaps: Int,
    val findings: List<KeyFindingDto>,
)
