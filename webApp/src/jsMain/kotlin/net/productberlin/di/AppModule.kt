package net.productberlin.di

import io.ktor.client.HttpClient
import net.productberlin.data.mock.createMockHttpClient
import net.productberlin.data.repository.KtorStartupRepository
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetWeeklyRanking
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Composition root. Transport selection stays outside domain and presentation. */
fun createAppContainer() =
    koinApplication {
        modules(
            module {
                single<HttpClient> { createMockHttpClient() } onClose { it?.close() }
                single<StartupRepository> { KtorStartupRepository(get()) }
                factory { GetWeeklyRanking(get()) }
            },
        )
    }
