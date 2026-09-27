package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogueTest {
    private val company = """{"id":"one","name":"One","description":"Description","category":"Tech"}"""

    @Test
    fun optionalAliasesAndContextDefaultToEmpty() {
        val entry = parseCatalogue("[$company]").single()
        assertEquals("one", entry.id)
        assertEquals(emptyList(), entry.aliases)
        assertEquals(emptyList(), entry.contextKeywords)
    }

    @Test
    fun preservesAliasesAndDisambiguationContext() {
        val entry = parseCatalogue("[" + company.dropLast(1) + """, "aliases":["One AI"], "contextKeywords":["accounting"]}]""").single()
        assertEquals(listOf("One AI"), entry.aliases)
        assertEquals(listOf("accounting"), entry.contextKeywords)
    }

    @Test
    fun rejectsEmptyOversizedDuplicateAndInvalidIdentityCatalogues() {
        for (json in listOf(
            "[]",
            List(501) { company }.joinToString(",", "[", "]"),
            "[$company,$company]",
            "[" + company.replace("\"one\"", "\"Bad ID\"") + "]",
        )) {
            assertFailsWith<IllegalArgumentException> { parseCatalogue(json) }
        }
    }

    @Test
    fun rejectsMissingBlankAndNonStringFields() {
        for (field in listOf("id", "name", "description", "category")) {
            val original =
                when (field) {
                    "id" -> "one"
                    "name" -> "One"
                    "description" -> "Description"
                    else -> "Tech"
                }
            for (value in listOf("null", "42", "true", "\"   \"")) {
                assertFailsWith<IllegalArgumentException> { parseCatalogue("[" + company.replace("\"$original\"", value) + "]") }
            }
        }
        assertFailsWith<IllegalArgumentException> { parseCatalogue("[{}]") }
    }

    @Test
    fun rejectsBlankAndNonStringAliasesAndContext() {
        for (field in listOf("aliases", "contextKeywords")) {
            for (value in listOf("null", "42", "\" \"")) {
                assertFailsWith<IllegalArgumentException> { parseCatalogue("[" + company.dropLast(1) + ",\"$field\":[$value]}]") }
            }
        }
    }
}
