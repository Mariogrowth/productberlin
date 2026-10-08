package net.productberlin.domain.usecase

import net.productberlin.domain.entity.MentionRanking
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate

/** Counts distinct headlines mentioning known companies, never publisher names or repeated words. */
class RankStartupMentions {
    operator fun invoke(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
    ): List<MentionRanking> =
        matches(catalogue, articles)
            // Ties go to the most recent coverage (ISO-8601 UTC timestamps sort chronologically), then the stable ID.
            .sortedWith(
                compareByDescending<MentionRanking> { it.mentionCount }
                    .thenByDescending { ranking -> ranking.articles.maxOf { it.publishedAt } }
                    .thenBy { it.company.id },
            ).take(10)

    /** Every catalogue company mentioned in [articles], with its distinct matching articles, in catalogue order. */
    fun matches(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
    ): List<MentionRanking> {
        require(catalogue.map { it.id }.distinct().size == catalogue.size) { "Duplicate catalogue identities" }
        val unique = articles.distinctBy { it.id }.distinctBy { normalized(it.headline) to normalized(it.source) }
        // Normalise each headline and each company's terms once: catalogues have hundreds of companies.
        val headlines = unique.map { it to normalized(it.headline) }
        return catalogue
            .map { company ->
                val terms = CompanyTerms(company)
                MentionRanking(company, headlines.filter { (_, title) -> terms.matches(title) }.map { it.first })
            }.filter { it.mentionCount > 0 }
    }

    fun mentions(
        company: StartupCandidate,
        article: NewsArticle,
    ): Boolean = CompanyTerms(company).matches(normalized(article.headline))

    /** A company's names and context keywords, normalised once. */
    private inner class CompanyTerms(
        company: StartupCandidate,
    ) {
        private val names = (company.aliases + company.name).filter { it.isNotBlank() }.map(::normalized)
        private val context = company.contextKeywords.filter { it.isNotBlank() }.map(::normalized)
        private val requiresContext = company.contextKeywords.isNotEmpty()

        fun matches(title: String): Boolean = names.any { title.contains(it) } && (!requiresContext || context.any { title.contains(it) })
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
