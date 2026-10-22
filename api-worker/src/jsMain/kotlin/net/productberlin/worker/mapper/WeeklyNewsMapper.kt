package net.productberlin.worker.mapper

import net.productberlin.contract.WeeklyNewsDto
import net.productberlin.contract.WeeklyStoryDto
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

internal fun WeeklyNews.toDto() =
    WeeklyNewsDto(
        weekLabel = weekLabel,
        stories = stories.map { it.toDto() },
        earlierLabel = earlierLabel,
        earlier = earlier.map { it.toDto() },
    )

private fun WeeklyStory.toDto() =
    WeeklyStoryDto(
        id,
        companies,
        category.name,
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
