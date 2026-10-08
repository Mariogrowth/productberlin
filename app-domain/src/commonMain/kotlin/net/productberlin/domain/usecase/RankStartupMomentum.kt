package net.productberlin.domain.usecase

import net.productberlin.domain.entity.MomentumRanking
import net.productberlin.domain.entity.NewsArticle
import net.productberlin.domain.entity.StartupCandidate

/**
 * Ranks companies by press momentum over the last four weeks of trusted coverage.
 *
 * - Each week, every publisher counts once per company, with the weight of its most important headline
 *   ([ClassifyHeadline]: funding 3, growth 2, other coverage 1; market notes, trouble and consumer content 0).
 * - Weeks count less with age: the latest week fully, then 75 %, 50 % and 25 %.
 * - A company needs counted coverage from at least two different publishers across the four weeks, so one article
 *   or one outlet alone never ranks.
 * - Ties go to the most recent counted article, then the stable company ID. Up to ten companies.
 */
class RankStartupMomentum(
    private val classify: ClassifyHeadline = ClassifyHeadline(),
    private val mentions: RankStartupMentions = RankStartupMentions(),
) {
    /** [weeks] holds each week's trusted articles, latest week first; weeks beyond the fourth are ignored. */
    operator fun invoke(
        catalogue: List<StartupCandidate>,
        weeks: List<List<NewsArticle>>,
    ): List<MomentumRanking> {
        val scores = mutableMapOf<String, Double>()
        val counted = mutableMapOf<String, MutableList<NewsArticle>>()
        val publishers = mutableMapOf<String, MutableSet<String>>()
        weeks.take(WEEK_WEIGHTS.size).forEachIndexed { age, articles ->
            for (match in mentions.matches(catalogue, articles)) {
                val id = match.company.id
                val strongest = mutableMapOf<String, Int>()
                for (article in match.articles) {
                    val weight = classify(article).weight
                    if (weight == 0) continue
                    val publisher = article.source.lowercase().trim()
                    strongest[publisher] = maxOf(strongest[publisher] ?: 0, weight)
                    counted.getOrPut(id) { mutableListOf() } += article
                }
                if (strongest.isEmpty()) continue
                scores[id] = (scores[id] ?: 0.0) + strongest.values.sum() * WEEK_WEIGHTS[age]
                publishers.getOrPut(id) { mutableSetOf() } += strongest.keys
            }
        }
        val byId = catalogue.associateBy { it.id }
        return scores
            .filter { (id, _) -> (publishers[id]?.size ?: 0) >= MIN_PUBLISHERS }
            .map { (id, score) ->
                val articles = counted.getValue(id).sortedWith(compareByDescending<NewsArticle> { it.publishedAt }.thenBy { it.id })
                MomentumRanking(byId.getValue(id), score, articles, publishers.getValue(id).size)
            }
            // ISO-8601 UTC timestamps sort chronologically.
            .sortedWith(
                compareByDescending<MomentumRanking> { it.score }
                    .thenByDescending { it.articles.first().publishedAt }
                    .thenBy { it.company.id },
            ).take(MAX_RANKED)
    }

    companion object {
        /** Latest week first. */
        val WEEK_WEIGHTS = listOf(1.0, 0.75, 0.5, 0.25)
        const val MIN_PUBLISHERS = 2
        const val MAX_RANKED = 10
    }
}
