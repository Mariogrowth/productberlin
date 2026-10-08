package net.productberlin.domain.entity

/** A company's press momentum over the scoring window, with the articles that counted, newest first. */
data class MomentumRanking(
    val company: StartupCandidate,
    val score: Double,
    val articles: List<NewsArticle>,
    val publishers: Int,
)
