package net.productberlin.domain.usecase

import net.productberlin.domain.entity.DealKind
import net.productberlin.domain.entity.HeadlineFacts
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyStory

/**
 * The older stories still worth catching up on, by weight rather than recency: funding rounds with an amount,
 * acquisitions and bids, and senior hires. Trouble, launches and ordinary coverage age badly and stay out.
 * Completed exits come first, then the largest amount, the widest coverage, the newest. One story per company.
 */
class SelectCatchUpStories(
    private val readFacts: ReadHeadlineFacts = ReadHeadlineFacts(),
) {
    operator fun invoke(stories: List<WeeklyStory>): List<WeeklyStory> {
        val notable =
            stories.mapNotNull { story ->
                val facts = readFacts(story)
                val keep =
                    when (story.category) {
                        NewsCategory.Funding -> facts.amount != null
                        NewsCategory.Growth -> facts.deal != null
                        NewsCategory.People -> facts.role != null
                        else -> false
                    }
                if (keep) story to facts else null
            }
        return notable
            .sortedWith(
                compareByDescending<Pair<WeeklyStory, HeadlineFacts>> { it.second.deal == DealKind.Exit }
                    .thenByDescending { it.second.amount?.millions ?: 0.0 }
                    .thenByDescending { it.first.alsoIn.size }
                    .thenByDescending { it.first.publishedAt },
            ).map { it.first }
            .distinctBy { it.companies.first() }
            .take(MAX_STORIES)
    }

    private companion object {
        /** More than the page shows, so the page can skip companies it already features. */
        const val MAX_STORIES = 8
    }
}
