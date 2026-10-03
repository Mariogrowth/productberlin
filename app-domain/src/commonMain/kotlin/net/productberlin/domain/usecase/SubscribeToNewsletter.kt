package net.productberlin.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository

/** Validates an address before any request is made and turns service failures into a retryable result. */
class SubscribeToNewsletter(
    private val repository: NewsletterRepository,
) {
    suspend operator fun invoke(input: String): SubscriptionResult {
        val email = EmailAddress.parse(input) ?: return SubscriptionResult.InvalidEmail
        return try {
            repository.requestSubscription(email)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            SubscriptionResult.Unavailable
        }
    }
}
