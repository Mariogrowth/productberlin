package net.productberlin.domain.usecase

import net.productberlin.domain.entity.MentionRanking
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate

/** Counts distinct headlines mentioning known companies, never publisher names or repeated words. */
class RankStartupMentions {
    operator fun invoke(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
    ): List<MentionRanking> {
        require(catalogue.map { it.id }.distinct().size == catalogue.size) { "Duplicate catalogue identities" }
        val unique = articles.distinctBy { it.id }.distinctBy { normalized(it.headline) to normalized(it.source) }
        return catalogue
            .map { company ->
                MentionRanking(company, unique.filter { mentions(company, it) })
            }.filter { it.mentionCount > 0 }
            // Ties go to the most recent coverage (ISO-8601 UTC timestamps sort chronologically), then the stable ID.
            .sortedWith(
                compareByDescending<MentionRanking> { it.mentionCount }
                    .thenByDescending { ranking -> ranking.articles.maxOf { it.publishedAt } }
                    .thenBy { it.company.id },
            ).take(10)
    }

    fun mentions(
        company: StartupCandidate,
        article: NewsArticle,
    ): Boolean {
        val title = normalized(article.headline)
        val matchesName =
            (company.aliases + company.name).any { alias ->
                alias.isNotBlank() && title.contains(normalized(alias))
            }
        val matchesContext =
            company.contextKeywords.isEmpty() ||
                company.contextKeywords.any {
                    it.isNotBlank() && title.contains(normalized(it))
                }
        return matchesName && matchesContext
    }

    private fun normalized(value: String): String =
        " " +
            value
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString("")
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") + " "
}
