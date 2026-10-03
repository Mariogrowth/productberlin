package net.productberlin.domain.repository

import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult

interface NewsletterRepository {
    /** Asks for a double opt-in confirmation email. Throws when the service cannot be reached. */
    suspend fun requestSubscription(email: EmailAddress): SubscriptionResult
}
