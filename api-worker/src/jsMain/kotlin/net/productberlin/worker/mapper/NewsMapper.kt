package net.productberlin.worker.mapper

import net.productberlin.contract.NewsDto
import net.productberlin.domain.entity.NewsArticle

internal fun NewsArticle.toDto() = NewsDto(id, headline, source, publishedAt)
