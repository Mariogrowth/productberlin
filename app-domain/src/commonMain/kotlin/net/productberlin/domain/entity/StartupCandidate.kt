package net.productberlin.domain.entity

/** An editorially maintained Berlin company identity and unambiguous matching aliases. */
data class StartupCandidate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val aliases: List<String> = emptyList(),
    val contextKeywords: List<String> = emptyList(),
)
