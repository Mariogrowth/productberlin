package net.productberlin.worker.hiring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JobBoardCatalogueTest {
    private fun catalogue(board: String?) =
        """[{"id":"acme","name":"Acme","description":"d","category":"c","aliases":[]${board?.let { ""","jobBoard":$it""" } ?: ""}},
            {"id":"beta","name":"Beta","description":"d","category":"c","aliases":[]}]"""

    @Test
    fun companiesWithABoardAreListedAndOthersSkipped() {
        assertEquals(
            listOf(JobBoard("acme", JobProvider.Ashby, "acme-gmbh_1")),
            parseJobBoards(catalogue("""{"provider":"ashby","handle":"acme-gmbh_1"}""")),
        )
        assertEquals(emptyList(), parseJobBoards(catalogue(null)))
    }

    @Test
    fun unknownProvidersAndUnsafeHandlesFailLoudly() {
        assertFailsWith<IllegalArgumentException> { parseJobBoards(catalogue("""{"provider":"workday","handle":"acme"}""")) }
        for (handle in listOf("acme/../x", "acme.evil.com/x", "", "-acme", "acme?x=1", "a".repeat(65))) {
            assertFailsWith<IllegalArgumentException>(handle) { parseJobBoards(catalogue("""{"provider":"lever","handle":"$handle"}""")) }
        }
    }

    @Test
    fun providerCodesMatchTheCollectorScript() {
        // scripts/collect-jobs.mjs has a reader for each of these codes; its tests assert the same list.
        assertEquals(
            listOf("personio", "ashby", "greenhouse", "lever", "workable", "recruitee", "smartrecruiters"),
            JobProvider.entries.map { it.code },
        )
    }
}
