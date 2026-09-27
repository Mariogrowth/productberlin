package net.productberlin.domain.entity

data class MentionRanking(
    val company: StartupCandidate,
    val articles: List<NewsArticle>,
) {
    val mentionCount: Int get() = articles.size
}
