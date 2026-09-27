package net.productberlin.di

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.productberlin.data.repository.KtorStartupRepository
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetWeeklyRanking

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
}
