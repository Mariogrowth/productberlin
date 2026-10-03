package net.productberlin.contract

import kotlinx.serialization.Serializable

/** `POST /api/subscriptions` body. */
@Serializable
data class SubscriptionRequestDto(
    val email: String,
)
