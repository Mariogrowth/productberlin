package net.productberlin.presentation

import net.productberlin.domain.entity.NewsArticle
import net.productberlin.presentation.designsystem.layouts.news.NewsStory

internal fun NewsArticle.toNewsStory(): NewsStory =
    NewsStory(
        id,
        headline,
        source,
        if (Regex("^\\d{4}-\\d{2}-\\d{2}T.*").matches(publishedAt)) publishedAt.take(10) else publishedAt,
        url,
        summary,
    )
