package net.productberlin.contract

import kotlinx.serialization.Serializable

/** A credential found in a public file: its type, a masked hint and where it was found. Never the full value. */
@Serializable
data class KeyFindingDto(
    val type: String,
    val masked: String,
    val location: String,
)
