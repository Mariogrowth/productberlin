package net.productberlin.worker.collection

internal data class NewsSearch(
    val query: String,
    val language: NewsLanguage,
)
