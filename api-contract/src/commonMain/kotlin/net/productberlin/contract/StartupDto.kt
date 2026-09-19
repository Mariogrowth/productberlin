package net.productberlin.contract

import kotlinx.serialization.Serializable

@Serializable
data class StartupDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val movement: Int? = null,
    val reason: String,
    val news: List<NewsDto> = emptyList(),
)
