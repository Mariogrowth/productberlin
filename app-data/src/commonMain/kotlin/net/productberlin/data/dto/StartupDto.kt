package net.productberlin.data.dto

import kotlinx.serialization.Serializable
import net.productberlin.domain.entity.Startup

@Serializable
internal data class StartupDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val movement: Int? = null,
    val reason: String,
    val news: List<NewsDto> = emptyList(),
) {
    fun toDomain() = Startup(id, name, description, category, movement, reason, news.map { it.toDomain() })
}
