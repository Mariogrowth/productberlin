package net.productberlin.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetWeeklyRanking
import net.productberlin.presentation.testing.ComponentTest
import react.create
import react.dom.flushSync
import web.html.HTMLButtonElement

class AppTest : ComponentTest() {
    private val ranking =
        WeeklyRanking(
            "Test week",
            listOf(Startup("one", "One", "Description", "Tech", null, "Reason", emptyList())),
            false,
            "2026-09-28T06:00:00Z",
            "Berlin",
            42,
        )

    @Test
    fun loadingTransitionsToLiveRankingWithAttribution() =
        runTest {
            val pending = CompletableDeferred<WeeklyRanking>()
            show { pending.await() }
            awaitUi { container.querySelector("[role=status]") != null }
            pending.complete(ranking)
            awaitUi { container.querySelector(".startup-list") != null }
            assertTrue(container.textContent.orEmpty().contains("One"))
            assertTrue(container.textContent.orEmpty().contains("42 articles reviewed"))
            assertTrue(container.textContent.orEmpty().contains("Google News"))
            assertTrue(!container.textContent.orEmpty().contains("Demo edition"))
            assertNull(container.querySelector("[role=alert]"))
        }

    @Test
    fun failureOffersRetryAndSuccessfulRetryClearsTheError() =
        runTest {
            var requests = 0
            show {
                requests++
                if (requests == 1) error("Unavailable") else ranking
            }
            awaitUi { container.querySelector("[role=alert] button") != null }
            val retry = container.querySelector("[role=alert] button")!!.unsafeCast<HTMLButtonElement>()
            flushSync { retry.click() }
            awaitUi { container.querySelector(".startup-list") != null }
            assertEquals(2, requests)
            assertNull(container.querySelector("[role=alert]"))
        }

    @Test
    fun emptyRankingHasAnEmptyStateInsteadOfAnError() =
        runTest {
            show { ranking.copy(startups = emptyList()) }
            awaitUi { container.textContent.orEmpty().contains("A quiet week") }
            assertNull(container.querySelector("[role=alert]"))
            assertNull(container.querySelector(".startup-list"))
        }

    @Test
    fun mockedRankingIsClearlyLabelled() =
        runTest {
            show { ranking.copy(isMock = true) }
            awaitUi { container.textContent.orEmpty().contains("Demo edition") }
            assertTrue(container.textContent.orEmpty().contains("fictional stories"))
            assertTrue(!container.textContent.orEmpty().contains("42 articles reviewed"))
        }

    @Test
    fun replacingUseCaseCancelsPreviousRequestAndIgnoresLateData() =
        runTest {
            val pending = CompletableDeferred<WeeklyRanking>()
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            show {
                started.complete(Unit)
                try {
                    pending.await()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            awaitUi { started.isCompleted }
            show { ranking.copy(weekLabel = "New request") }
            awaitUi { container.textContent.orEmpty().contains("New request") && cancelled.isCompleted }
            pending.complete(ranking.copy(weekLabel = "Stale request"))
            tick()
            assertTrue(!container.textContent.orEmpty().contains("Stale request"))
            assertTrue(container.textContent.orEmpty().contains("New request"))
        }

    private fun show(
        confirmed: Boolean = false,
        load: suspend () -> WeeklyRanking,
    ) {
        val useCase =
            GetWeeklyRanking(
                object : StartupRepository {
                    override suspend fun getWeeklyRanking() = load()
                },
            )
        render(
            App.create {
                getRanking = useCase
                subscriptionConfirmed = confirmed
            },
        )
    }

    @Test
    fun confirmedSubscribersSeeADismissibleBannerAboveTheHeaderAndNotBesideThePill() =
        runTest {
            show(confirmed = true) { ranking }
            val banner = assertNotNull(container.querySelector(".confirmation .pb-banner"))
            assertEquals(SUBSCRIPTION_CONFIRMED, banner.querySelector(".pb-banner-message")?.textContent)
            assertNotNull(container.querySelector(".confirmation + header"), "Banner sits directly above the header")
            assertEquals("", container.querySelector(".pb-email-signup-message")?.textContent)
            flushSync { banner.querySelector("button").unsafeCast<HTMLButtonElement>().click() }
            assertNull(container.querySelector(".pb-banner"))
        }

    @Test
    fun regularVisitsShowNoBanner() =
        runTest {
            show { ranking }
            assertNull(container.querySelector(".pb-banner"))
        }

    // React effects use the real browser scheduler, not the coroutine test scheduler.
    private suspend fun tick() = withContext(Dispatchers.Default) { delay(10) }

    private suspend fun awaitUi(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            tick()
        }
        assertTrue(predicate(), "Expected UI state was not rendered")
    }
}
