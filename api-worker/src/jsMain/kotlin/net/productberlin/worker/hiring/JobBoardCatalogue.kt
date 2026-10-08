package net.productberlin.worker.hiring

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The job boards listed in the catalogue. Companies without a `jobBoard` are simply not counted. An unknown provider
 * or a handle that could alter the feed URL fails loudly, because the catalogue is edited by hand.
 */
internal fun parseJobBoards(catalogueJson: String): List<JobBoard> =
    Json.parseToJsonElement(catalogueJson).jsonArray.mapNotNull { item ->
        val company = item.jsonObject
        val board = company["jobBoard"]?.jsonObject ?: return@mapNotNull null
        val id = requireNotNull(company["id"]).jsonPrimitive.content
        val code = requireNotNull(board["provider"]).jsonPrimitive.content
        val handle = requireNotNull(board["handle"]).jsonPrimitive.content
        val provider = requireNotNull(JobProvider.of(code)) { "Unknown job board provider '$code' for $id" }
        require(HANDLE.matches(handle)) { "Invalid job board handle for $id" }
        JobBoard(id, provider, handle)
    }

/** Letters, digits, dot, underscore and hyphen: safe in a subdomain or a URL path segment. */
private val HANDLE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
