package net.productberlin.worker.collection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.productberlin.domain.entity.StartupCandidate

internal fun parseCatalogue(json: String): List<StartupCandidate> =
    Json
        .parseToJsonElement(json)
        .jsonArray
        .map { item ->
            val value = item.jsonObject

            fun string(element: JsonElement): String =
                element.jsonPrimitive.let {
                    require(it.isString && it.content.isNotBlank()) { "Catalogue fields must be nonblank strings" }
                    it.content
                }

            fun field(name: String) = string(requireNotNull(value[name]))
            StartupCandidate(
                field("id"),
                field("name"),
                field("description"),
                field("category"),
                value["aliases"]?.jsonArray?.map { string(it) } ?: emptyList(),
                value["contextKeywords"]?.jsonArray?.map { string(it) } ?: emptyList(),
                value["domain"]?.let { string(it) },
            )
        }.also { catalogue ->
            require(catalogue.isNotEmpty() && catalogue.size <= 500) { "Catalogue must have 1–500 companies" }
            require(catalogue.map { it.id }.distinct().size == catalogue.size) { "Duplicate catalogue IDs" }
            require(catalogue.all { Regex("[a-z0-9-]+").matches(it.id) }) { "Invalid catalogue ID" }
            require(catalogue.all { it.domain?.let(DOMAIN::matches) ?: true }) { "Invalid catalogue domain" }
        }

/** A bare lowercase hostname such as `n26.com`: no scheme, path, port or credentials. */
private val DOMAIN = Regex("([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}")
