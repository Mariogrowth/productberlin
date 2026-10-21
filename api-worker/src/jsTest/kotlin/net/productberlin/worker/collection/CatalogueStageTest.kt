package net.productberlin.worker.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogueStageTest {
    private fun company(
        id: String,
        stage: String?,
    ) = """{"id":"$id","name":"$id","description":"d","category":"c","aliases":[]${stage?.let { ""","stage":"$it"""" } ?: ""}}"""

    @Test
    fun scaleupsAreMarkedAndEveryoneElseIsAStartup() {
        val catalogue = parseCatalogue("[${company("trade-republic", "scaleup")},${company("mika", "startup")},${company("nox", null)}]")
        assertEquals(listOf(true, false, false), catalogue.map { it.isScaleup })
    }

    @Test
    fun anUnknownStageFailsLoudly() {
        assertFailsWith<IllegalArgumentException> { parseCatalogue("[${company("x", "growth")}]") }
    }
}
