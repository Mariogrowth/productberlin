package net.productberlin.domain.entity

/** A credential found in a public file. Only a masked hint is ever kept, never the full value. */
data class KeyFinding(
    val type: KeyType,
    val masked: String,
    val location: String,
)
