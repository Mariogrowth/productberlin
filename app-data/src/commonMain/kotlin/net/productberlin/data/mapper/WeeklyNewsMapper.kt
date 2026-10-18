package net.productberlin.data.mapper

import net.productberlin.contract.WeeklyNewsDto
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyStory

/** Stories in a category this client does not know (from a newer Worker) are skipped. */
fun WeeklyNewsDto.toDomain() =
    WeeklyNews(
        weekLabel = weekLabel,
        stories =
            stories.mapNotNull { story ->
                val category = NewsCategory.entries.firstOrNull { it.name == story.category } ?: return@mapNotNull null
                WeeklyStory(
                    story.id,
                    story.companies,
                    category,
                    story.headline,
                    story.source,
                    story.publishedAt,
                    story.url,
                    story.translatedHeadline,
                    story.alsoIn,
                    story.companyId,
                    story.logoUrl,
                    story.logoFallbackUrl,
                )
            },
    )
