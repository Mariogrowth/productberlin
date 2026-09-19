package net.productberlin.data.mapper

import net.productberlin.contract.NewsDto
import net.productberlin.domain.entity.NewsArticle

internal fun NewsDto.toDomain() = NewsArticle(id, headline, source, publishedAt)
