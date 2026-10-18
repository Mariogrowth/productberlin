package net.productberlin.domain.usecase

import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyStory

/**
 * Turns a week's articles about catalogue companies into categorised stories.
 * - Each article is classified with [ClassifyHeadline]; share-price notes and consumer content are dropped.
 * - The same news from several outlets becomes one story: same companies and category, and either a shared amount
 *   ("€17 million", "€17m") or mostly the same words. The first report leads; the others are listed as "also in".
 * - Stories come in category order, newest first within a category.
 */
class CategorizeWeeklyNews(
    private val classify: ClassifyHeadline = ClassifyHeadline(),
    private val mentions: RankStartupMentions = RankStartupMentions(),
) {
    operator fun invoke(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
    ): List<WeeklyStory> {
        val companiesByArticle = linkedMapOf<String, MutableList<StartupCandidate>>()
        val byId = articles.associateBy { it.id }
        for (match in mentions.matches(catalogue, articles)) {
            for (article in match.articles) companiesByArticle.getOrPut(article.id) { mutableListOf() } += match.company
        }
        val candidates =
            companiesByArticle.mapNotNull { (id, companies) ->
                val article = byId.getValue(id)
                val category = classify(article).newsCategory ?: return@mapNotNull null
                Triple(article, companies.toList(), category)
            }
        val groups = mutableListOf<MutableList<Triple<NewsArticle, List<StartupCandidate>, NewsCategory>>>()
        for (candidate in candidates.sortedWith(compareBy({ it.first.publishedAt }, { it.first.id }))) {
            val group =
                groups.firstOrNull { existing ->
                    val lead = existing.first()
                    lead.second.map { it.id } == candidate.second.map { it.id } && lead.third == candidate.third &&
                        sameNews(lead.first, candidate.first)
                }
            if (group != null) group += candidate else groups += mutableListOf(candidate)
        }
        return groups
            .map { group ->
                val (lead, companies, category) = group.first()
                WeeklyStory(
                    id = lead.id,
                    companies = companies.map { it.name },
                    category = category,
                    headline = lead.headline,
                    source = lead.source,
                    publishedAt = lead.publishedAt,
                    url = lead.url,
                    translatedHeadline = lead.translatedHeadline,
                    alsoIn =
                        group
                            .drop(1)
                            .map { it.first.source }
                            .filter { it != lead.source }
                            .distinct(),
                    companyId = companies.first().id,
                )
            }.sortedWith(compareBy<WeeklyStory> { it.category.ordinal }.thenByDescending { it.publishedAt }.thenBy { it.id })
    }

    private fun sameNews(
        a: NewsArticle,
        b: NewsArticle,
    ): Boolean {
        val amounts = amounts(a).intersect(amounts(b))
        if (amounts.isNotEmpty()) return true
        val wordsA = words(a)
        val wordsB = words(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        return wordsA.intersect(wordsB).size.toDouble() / wordsA.union(wordsB).size >= SAME_WORDS
    }

    /** Numbers that are not years: "17", "100", "6.3". */
    private fun amounts(article: NewsArticle): Set<String> =
        NUMBER
            .findAll(text(article))
            .map { it.value.replace(',', '.') }
            .filterNot { YEAR.matches(it) }
            .toSet()

    private fun words(article: NewsArticle): Set<String> =
        text(article)
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }
            .toSet()

    /** The English version when there is one, so German and English reports of the same news compare alike. */
    private fun text(article: NewsArticle) = article.translatedHeadline ?: article.headline

    private companion object {
        const val SAME_WORDS = 0.5
        val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
        val YEAR = Regex("(19|20)\\d\\d")
    }
}
