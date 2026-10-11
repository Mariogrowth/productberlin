package net.productberlin.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import net.productberlin.contract.KeyScanDto
import net.productberlin.data.mapper.toDomain
import net.productberlin.domain.entity.KeyScanResult
import net.productberlin.domain.repository.KeyScanRepository

/** Asks the Worker to scan a site; the browser never fetches other sites itself. */
class KtorKeyScanRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : KeyScanRepository {
    override suspend fun scan(domain: String): KeyScanResult {
        val response =
            client.get("${baseUrl.trimEnd('/')}/api/key-check") {
                expectSuccess = false
                parameter("domain", domain)
            }
        return when (response.status) {
            HttpStatusCode.OK -> KeyScanResult.Completed(response.body<KeyScanDto>().toDomain())
            HttpStatusCode.BadRequest -> KeyScanResult.InvalidDomain
            HttpStatusCode.BadGateway -> KeyScanResult.Unreachable
            else -> KeyScanResult.Unavailable
        }
    }
}
