package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository

class GetWeeklyRankingTest {
    private val startup = Startup("one", "One", "Description", "Software", 2, "Reason", emptyList())

    @Test
    fun preservesPublishedOrderAndAllowsAnEmptyWeek() =
        runTest {
            val published = WeeklyRanking("Demo week", listOf(startup.copy(id = "two"), startup))
            val repository =
                object : StartupRepository {
                    override suspend fun getWeeklyRanking() = published
                }
            assertEquals(published, GetWeeklyRanking(repository)())
            val empty =
                object : StartupRepository {
                    override suspend fun getWeeklyRanking() = WeeklyRanking("Quiet week", emptyList())
                }
            assertEquals(emptyList(), GetWeeklyRanking(empty)().startups)
        }

    @Test
    fun rejectsDuplicateStartupIdentities() =
        runTest {
            val repository =
                object : StartupRepository {
                    override suspend fun getWeeklyRanking() = WeeklyRanking("Demo week", listOf(startup, startup))
                }
            assertFailsWith<IllegalArgumentException> { GetWeeklyRanking(repository)() }
        }

    @Test
    fun preservesFailuresAndCancellationForTheCaller() =
        runTest {
            for (failure in listOf(IllegalStateException("Unavailable"), CancellationException("Cancelled"))) {
                val repository =
                    object : StartupRepository {
                        override suspend fun getWeeklyRanking(): WeeklyRanking = throw failure
                    }
                val actual = assertFailsWith<Exception> { GetWeeklyRanking(repository)() }
                assertSame(failure, actual)
            }
        }
}
