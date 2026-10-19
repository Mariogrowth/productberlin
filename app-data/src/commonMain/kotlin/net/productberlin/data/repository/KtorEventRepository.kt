package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import net.productberlin.contract.EventsDto
import net.productberlin.data.mapper.toDomain
import net.productberlin.domain.entity.StartupEvent
import net.productberlin.domain.repository.EventRepository

class KtorEventRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : EventRepository {
    override suspend fun getUpcomingEvents(): List<StartupEvent> =
        client.get("${baseUrl.trimEnd('/')}/api/events").body<EventsDto>().toDomain()
}
