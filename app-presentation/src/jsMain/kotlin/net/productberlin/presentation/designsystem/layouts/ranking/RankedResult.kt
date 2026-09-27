package net.productberlin.presentation.designsystem.layouts.ranking

/** Presentation data, deliberately independent of the backend's company model. */
data class RankedResult(
    val id: String,
    val rank: Int,
    val name: String,
    val description: String,
    val movement: Int?,
    val reason: String,
)
