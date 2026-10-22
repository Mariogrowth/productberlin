package net.productberlin.data.mapper

import net.productberlin.contract.WeeklyNewsDto
import net.productberlin.contract.WeeklyStoryDto
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

/** Stories in a category this client does not know (from a newer Worker) are skipped. */
fun WeeklyNewsDto.toDomain() =
    WeeklyNews(
        weekLabel = weekLabel,
        stories = stories.mapNotNull { it.toDomain() },
        earlierLabel = earlierLabel,
        earlier = earlier.mapNotNull { it.toDomain() },
    )

private fun WeeklyStoryDto.toDomain(): WeeklyStory? {
    val category = NewsCategory.entries.firstOrNull { it.name == category } ?: return null
    return WeeklyStory(
        id,
        companies,
        category,
        headline,
        source,
        publishedAt,
        url,
        translatedHeadline,
        alsoIn,
        companyId,
        logoUrl,
        logoFallbackUrl,
    )
}
