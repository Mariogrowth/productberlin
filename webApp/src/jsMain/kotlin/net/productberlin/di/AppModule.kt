package net.productberlin.di

import io.ktor.client.HttpClient
import net.productberlin.data.http.createBrowserHttpClient
import net.productberlin.data.repository.KtorNewsletterRepository
import net.productberlin.data.repository.KtorStartupRepository
import net.productberlin.domain.repository.NewsletterRepository
import net.productberlin.domain.repository.StartupRepository
import net.productberlin.domain.usecase.GetWeeklyRanking
import net.productberlin.domain.usecase.SubscribeToNewsletter
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import web.window.window

/** Composition root. Transport selection stays outside domain and presentation. */
fun createAppContainer() =
    koinApplication {
        modules(
            module {
                single<HttpClient> { createBrowserHttpClient() } onClose { it?.close() }
                single<StartupRepository> { KtorStartupRepository(get(), window.location.origin) }
                factory { GetWeeklyRanking(get()) }
                single<NewsletterRepository> { KtorNewsletterRepository(get(), window.location.origin) }
                factory { SubscribeToNewsletter(get()) }
            },
        )
    }
