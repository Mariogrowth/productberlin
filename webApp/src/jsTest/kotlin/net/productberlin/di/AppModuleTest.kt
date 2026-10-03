package net.productberlin.di

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.productberlin.data.repository.KtorNewsletterRepository
import net.productberlin.data.repository.KtorStartupRepository
import net.productberlin.domain.repository.NewsletterRepository
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetWeeklyRanking
import net.productberlin.domain.usecase.SubscribeToNewsletter

class AppModuleTest {
    @Test
    fun compositionResolvesUseCasesWithSharedHttpAndRepositoryInstances() {
        val container = createAppContainer()
        try {
            assertSame(container.koin.get<HttpClient>(), container.koin.get<HttpClient>())
            assertSame(container.koin.get<StartupRepository>(), container.koin.get<StartupRepository>())
            assertTrue(container.koin.get<StartupRepository>() is KtorStartupRepository)
            assertNotSame(container.koin.get<GetWeeklyRanking>(), container.koin.get<GetWeeklyRanking>())
        } finally {
            container.close()
        }
    }

    @Test
    fun independentContainersDoNotShareClientLifetimes() {
        val first = createAppContainer()
        val second = createAppContainer()
        try {
            assertNotSame(first.koin.get<HttpClient>(), second.koin.get<HttpClient>())
            first.close()
            second.koin.get<GetWeeklyRanking>()
        } finally {
            second.close()
        }
    }

    @Test
    fun compositionResolvesTheNewsletterUseCaseOverTheSharedClient() {
        val container = createAppContainer()
        try {
            val repository = container.koin.get<NewsletterRepository>()
            assertTrue(repository is KtorNewsletterRepository)
            assertSame(repository, container.koin.get<NewsletterRepository>())
            assertNotSame(container.koin.get<SubscribeToNewsletter>(), container.koin.get<SubscribeToNewsletter>())
        } finally {
            container.close()
        }
    }
}
