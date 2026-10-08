package net.productberlin.worker.hiring

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HiringEndpointTest {
    private val token = "t".repeat(40)
    private val auth = "Bearer $token"
    private val tuesday = Date.parse("2026-10-06T08:07:00Z")
    private val boards =
        listOf(
            JobBoard("acme", JobProvider.Ashby, "acme"),
            JobBoard("beta", JobProvider.Personio, "beta-gmbh"),
            JobBoard("gamma", JobProvider.Greenhouse, "gamma"),
        )

    private class Fake : HiringRepository {
        val weeks = mutableMapOf<String, Pair<List<HiringCount>, Int>>()

        override suspend fun isRecorded(week: String) = week in weeks

        override suspend fun record(
            week: String,
            counts: List<HiringCount>,
            failed: Int,
            recordedAt: String,
        ): Boolean {
            if (week in weeks) return false
            weeks[week] = counts to failed
            return true
        }
    }

    private fun endpoint(
        repository: Fake = Fake(),
        configured: String? = token,
    ) = HiringEndpoint(configured, repository, boards, tuesday)

    private fun result(
        id: String,
        provider: String,
        handle: String,
        total: Any?,
        berlin: Any?,
    ) = """{"startupId":"$id","provider":"$provider","handle":"$handle","totalJobs":$total,"berlinJobs":$berlin}"""

    private fun post(
        vararg results: String,
        week: String = "2026-10-05",
    ) = """{"week":"$week","results":[${results.joinToString(",")}]}"""

    @Test
    fun requiresTheCollectorTokenAndAKnownMethod() =
        runTest {
            assertEquals(503, endpoint(configured = null).handle("GET", auth, "").status)
            assertEquals(503, endpoint(configured = "short").handle("GET", "Bearer short", "").status)
            assertEquals(401, endpoint().handle("GET", "Bearer wrong", "").status)
            assertEquals(401, endpoint().handle("GET", null, "").status)
            val wrongMethod = endpoint().handle("DELETE", auth, "")
            assertEquals(405, wrongMethod.status)
            assertEquals("GET, POST", wrongMethod.allow)
        }

    @Test
    fun plansEveryCatalogueBoardForThisWeekUntilItIsRecorded() =
        runTest {
            val repository = Fake()
            val plan = JSON.parse<dynamic>(endpoint(repository).handle("GET", auth, "").body)
            assertEquals(true, plan.due)
            assertEquals("2026-10-05", plan.week, "the Monday of the current UTC week")
            assertEquals(
                listOf("acme:ashby:acme", "beta:personio:beta-gmbh", "gamma:greenhouse:gamma"),
                plan.boards.unsafeCast<Array<dynamic>>().map { "${it.startupId}:${it.provider}:${it.handle}" },
            )
            endpoint(
                repository,
            ).handle("POST", auth, post(result("acme", "ashby", "acme", 3, 1), result("beta", "personio", "beta-gmbh", 0, 0)))
            assertEquals("""{"due":false,"reason":"recorded","week":"2026-10-05"}""", endpoint(repository).handle("GET", auth, "").body)
        }

    @Test
    fun storesValidCountsOnceAndCountsTheRestAsMissing() =
        runTest {
            val repository = Fake()
            val body =
                post(
                    result("acme", "ashby", "acme", 12, 4),
                    result("beta", "personio", "beta-gmbh", 7, 7),
                    """{"startupId":"gamma","provider":"greenhouse","handle":"gamma","error":"HTTP 503"}""",
                )
            val first = endpoint(repository).handle("POST", auth, body)
            assertEquals(200, first.status)
            assertEquals("""{"outcome":"recorded","week":"2026-10-05","boards":2,"failed":1}""", first.body)
            assertEquals(
                listOf(HiringCount("acme", JobProvider.Ashby, 12, 4), HiringCount("beta", JobProvider.Personio, 7, 7)) to 1,
                repository.weeks["2026-10-05"],
            )
            assertTrue(endpoint(repository).handle("POST", auth, body).body.contains("\"already_recorded\""))
        }

    @Test
    fun impossibleOrUnplannedCountsAreDroppedAndTooFewSuccessesStoreNothing() =
        runTest {
            val repository = Fake()
            val response =
                endpoint(repository).handle(
                    "POST",
                    auth,
                    post(
                        result("acme", "ashby", "acme", 3, 4), // more Berlin roles than roles
                        result("beta", "personio", "other-handle", 5, 1), // not the planned board
                        result("gamma", "greenhouse", "gamma", 2.5, 1), // not a whole number
                        result("zeta", "ashby", "zeta", 5, 1), // not in the catalogue
                    ),
                )
            assertEquals(502, response.status)
            assertEquals("""{"error":"too_many_failures","recorded":0,"boards":3}""", response.body)
            assertNull(repository.weeks["2026-10-05"])

            val negative = endpoint(repository).handle("POST", auth, post(result("acme", "ashby", "acme", -1, 0)))
            assertEquals(502, negative.status)
        }

    @Test
    fun aPlanFromAnotherWeekOrABrokenBodyIsRejected() =
        runTest {
            val repository = Fake()
            val stale = endpoint(repository).handle("POST", auth, post(result("acme", "ashby", "acme", 3, 1), week = "2026-09-28"))
            assertEquals(409, stale.status)
            assertEquals(400, endpoint(repository).handle("POST", auth, "{").status)
            assertEquals(413, endpoint(repository).handle("POST", auth, " ".repeat(1_000_001)).status)
            assertTrue(repository.weeks.isEmpty())
        }
}
