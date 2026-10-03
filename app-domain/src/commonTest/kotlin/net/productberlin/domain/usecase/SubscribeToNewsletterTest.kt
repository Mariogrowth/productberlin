package net.productberlin.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.productberlin.domain.entity.EmailAddress
import net.productberlin.domain.entity.SubscriptionResult
import net.productberlin.domain.repository.NewsletterRepository

class SubscribeToNewsletterTest {
    private class Recording(
        private val respond: suspend () -> SubscriptionResult = { SubscriptionResult.ConfirmationSent },
    ) : NewsletterRepository {
        val requested = mutableListOf<String>()

        override suspend fun requestSubscription(email: EmailAddress): SubscriptionResult {
            requested += email.value
            return respond()
        }
    }

    @Test
    fun trimsValidAddressesBeforeRequestingConfirmation() =
        runTest {
            val repository = Recording()
            assertEquals(SubscriptionResult.ConfirmationSent, SubscribeToNewsletter(repository)("  name@domain.de "))
            assertEquals(listOf("name@domain.de"), repository.requested)
        }

    @Test
    fun malformedOrOversizedAddressesNeverReachTheService() =
        runTest {
            val repository = Recording()
            val oversized = "a".repeat(250) + "@b.de"
            for (input in listOf("", "   ", "name", "name@", "name@domain", "a b@c.de", "@domain.de", oversized)) {
                assertEquals(SubscriptionResult.InvalidEmail, SubscribeToNewsletter(repository)(input), input)
                assertNull(EmailAddress.parse(input), input)
            }
            assertEquals(emptyList(), repository.requested)
        }

    @Test
    fun passesServiceOutcomesThroughAndTurnsFailuresIntoUnavailable() =
        runTest {
            for (outcome in SubscriptionResult.entries) {
                assertEquals(outcome, SubscribeToNewsletter(Recording { outcome })("name@domain.de"))
            }
            assertEquals(SubscriptionResult.Unavailable, SubscribeToNewsletter(Recording { error("offline") })("name@domain.de"))
        }

    @Test
    fun cancellationIsNotSwallowed() =
        runTest {
            assertFailsWith<CancellationException> {
                SubscribeToNewsletter(Recording { throw CancellationException("left page") })("name@domain.de")
            }
        }
}
