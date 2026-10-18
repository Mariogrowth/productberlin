package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import net.productberlin.contract.WeeklyNewsDto
import net.productberlin.data.mapper.toDomain
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.repository.NewsRepository

class KtorNewsRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : NewsRepository {
    override suspend fun getWeeklyNews(): WeeklyNews =
        client.get("${baseUrl.trimEnd('/')}/api/news/weekly").body<WeeklyNewsDto>().toDomain()
}
