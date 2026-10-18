package net.productberlin.domain.usecase

import net.productberlin.domain.entity.FeedStory
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.StartupCandidate
import net.productberlin.domain.entity.WeeklyStory

/**
 * Turns a week's news into categorised stories.
 * - [NewsArticle]s (Google News, and feed items without a model verdict) count for the catalogue companies their
 *   headline names, classified with [ClassifyHeadline]; share-price notes and consumer content are dropped.
 * - [FeedStory]s arrive already judged: company (catalogue or new) and category.
 * - The same news from several outlets or sources becomes one story: same companies and category, and either a shared
 *   amount ("€17 million", "€17m") or mostly the same words. The first report leads; the others are "also in".
 * - Stories come in category order, newest first within a category.
 */
class CategorizeWeeklyNews(
    private val classify: ClassifyHeadline = ClassifyHeadline(),
    private val mentions: RankStartupMentions = RankStartupMentions(),
) {
    private data class Company(
        val id: String?,
        val name: String,
    ) {
        /** Catalogue companies by ID; new ones by name, so two feeds naming the same new startup still merge. */
        val key: String get() = id ?: "new:${name.lowercase().trim()}"
    }

    private data class Candidate(
        val article: NewsArticle,
        val companies: List<Company>,
        val category: NewsCategory,
    )

    operator fun invoke(
        catalogue: List<StartupCandidate>,
        articles: List<NewsArticle>,
        feedStories: List<FeedStory> = emptyList(),
    ): List<WeeklyStory> {
        val companiesByArticle = linkedMapOf<String, MutableList<Company>>()
        val byId = articles.associateBy { it.id }
        for (match in mentions.matches(catalogue, articles)) {
            for (article in match.articles) {
                companiesByArticle.getOrPut(article.id) { mutableListOf() } += Company(match.company.id, match.company.name)
            }
        }
        val fromArticles =
            companiesByArticle.mapNotNull { (id, companies) ->
                val article = byId.getValue(id)
                val category = classify(article).newsCategory ?: return@mapNotNull null
                Candidate(article, companies.toList(), category)
            }
        val catalogueIds = catalogue.map { it.id }.toSet()
        val fromFeeds =
            feedStories.map { story ->
                val translated = story.originalHeadline != null && story.originalHeadline != story.headline
                Candidate(
                    NewsArticle(
                        story.id,
                        if (translated) story.originalHeadline!! else story.headline,
                        story.source,
                        story.publishedAt,
                        story.url,
                        translatedHeadline = if (translated) story.headline else null,
                    ),
                    listOf(Company(story.companyId?.takeIf { it in catalogueIds }, story.companyName)),
                    story.category,
                )
            }
        val groups = mutableListOf<MutableList<Candidate>>()
        for (candidate in (fromArticles + fromFeeds).sortedWith(compareBy({ it.article.publishedAt }, { it.article.id }))) {
            val group =
                groups.firstOrNull { existing ->
                    val lead = existing.first()
                    lead.companies.map { it.key } == candidate.companies.map { it.key } && lead.category == candidate.category &&
                        sameNews(lead.article, candidate.article)
                }
            if (group != null) group += candidate else groups += mutableListOf(candidate)
        }
        return groups
            .map { group ->
                val lead = group.first()
                val first = lead.companies.first()
                WeeklyStory(
                    id = lead.article.id,
                    companies = lead.companies.map { it.name },
                    category = lead.category,
                    headline = lead.article.headline,
                    source = lead.article.source,
                    publishedAt = lead.article.publishedAt,
                    url = lead.article.url,
                    translatedHeadline = lead.article.translatedHeadline,
                    alsoIn =
                        group
                            .drop(1)
                            .map { it.article.source }
                            .filter { it != lead.article.source }
                            .distinct(),
                    companyId = first.id ?: "",
                    isNew = first.id == null,
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
