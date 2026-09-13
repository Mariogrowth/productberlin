package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import net.productberlin.data.dto.RankingDto
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository

class KtorStartupRepository(
    private val client: HttpClient,
) : StartupRepository {
    override suspend fun getWeeklyRanking(): WeeklyRanking =
        client.get("https://productberlin.invalid/api/rankings/weekly").body<RankingDto>().toDomain()
}
