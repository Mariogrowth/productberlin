package net.productberlin.domain.usecase

import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.repository.NewsRepository

class GetWeeklyNews(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(): WeeklyNews = repository.getWeeklyNews()
}
