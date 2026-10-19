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
import net.productberlin.domain.entity.NewsCategory
import net.productberlin.domain.entity.Startup
import net.productberlin.domain.entity.WeeklyNews
import net.productberlin.domain.entity.WeeklyRanking
import net.productberlin.domain.entity.WeeklyStory
import net.productberlin.domain.repository.EventRepository
import net.productberlin.domain.repository.NewsRepository
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetUpcomingEvents
import net.productberlin.domain.usecase.GetWeeklyNews
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
        news: suspend () -> WeeklyNews = { WeeklyNews("Test week", emptyList()) },
        load: suspend () -> WeeklyRanking,
    ) {
        val useCase =
            GetWeeklyRanking(
                object : StartupRepository {
                    override suspend fun getWeeklyRanking() = load()
                },
            )
        val eventsUseCase =
            GetUpcomingEvents(
                object : EventRepository {
                    override suspend fun getUpcomingEvents() = emptyList<net.productberlin.domain.entity.StartupEvent>()
                },
            )
        val newsUseCase =
            GetWeeklyNews(
                object : NewsRepository {
                    override suspend fun getWeeklyNews() = news()
                },
            )
        render(
            App.create {
                getRanking = useCase
                getNews = newsUseCase
                getEvents = eventsUseCase
                subscriptionConfirmed = confirmed
            },
        )
    }

    private val story =
        WeeklyStory("f1", listOf("finmid"), NewsCategory.Funding, "finmid raises €17M", "Startbase", "2026-10-07T09:00:00.000Z")

    @Test
    fun liveWeeksShowTheNewsSectionBetweenTheRankingAndTheSignup() =
        runTest {
            show(news = { WeeklyNews("Test week", listOf(story)) }) { ranking }
            awaitUi { container.querySelector(".weekly-news") != null }
            assertNotNull(container.querySelector(".ranking-layout + .weekly-news + .newsletter"), "ranking, then news, then the signup")
            assertEquals("This week in Berlin startups", container.querySelector(".weekly-news h2")?.textContent)
        }

    @Test
    fun demoEditionsQuietWeeksAndFailedNewsShowNoSection() =
        runTest {
            for ((rankingFor, news) in listOf<Pair<WeeklyRanking, suspend () -> WeeklyNews>>(
                ranking.copy(isMock = true) to { WeeklyNews("Test week", listOf(story)) },
                ranking to { WeeklyNews("Test week", emptyList()) },
                ranking to { error("News unavailable") },
            )) {
                show(news = news) { rankingFor }
                awaitUi { container.querySelector(".startup-list") != null }
                tick()
                assertNull(container.querySelector(".weekly-news"))
                assertNull(container.querySelector("[role=alert]"), "a news failure never looks like a ranking failure")
            }
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
    fun footerEndsWithTheListExplanationAndThePrivacyLinkStaysBesideTheSignup() =
        runTest {
            show { ranking }
            val footer = assertNotNull(container.querySelector("footer"))
            assertTrue(!footer.textContent.orEmpty().contains("Made for the city"))
            assertNull(footer.querySelector("a[href='/privacy']"))
            assertNotNull(container.querySelector(".newsletter a[href='/privacy']"))
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
