package net.productberlin.worker.mapper

import net.productberlin.contract.WeeklyNewsDto
import net.productberlin.contract.WeeklyStoryDto
import net.productberlin.domain.entity.WeeklyNews

internal fun WeeklyNews.toDto() =
    WeeklyNewsDto(
        weekLabel = weekLabel,
        stories =
            stories.map {
                WeeklyStoryDto(
                    it.id,
                    it.companies,
                    it.category.name,
                    it.headline,
                    it.source,
                    it.publishedAt,
                    it.url,
                    it.translatedHeadline,
                    it.alsoIn,
                    it.companyId,
                    it.logoUrl,
                    it.logoFallbackUrl,
                    it.isNew,
                )
            },
    )
