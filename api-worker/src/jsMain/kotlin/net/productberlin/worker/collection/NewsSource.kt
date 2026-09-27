package net.productberlin.worker.collection

import net.productberlin.domain.entity.NewsArticle

internal interface NewsSource {
    suspend fun search(search: NewsSearch): List<NewsArticle>
}
