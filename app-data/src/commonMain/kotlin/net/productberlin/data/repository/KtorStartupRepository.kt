package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import net.productberlin.contract.RankingDto
import net.productberlin.data.mapper.toDomain
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository

class KtorStartupRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : StartupRepository {
    override suspend fun getWeeklyRanking(): WeeklyRanking =
        client.get("${baseUrl.trimEnd('/')}/api/rankings/weekly").body<RankingDto>().toDomain()
}
