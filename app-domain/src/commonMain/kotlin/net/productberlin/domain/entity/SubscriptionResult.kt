package net.productberlin.domain.entity

/** Outcome of a newsletter sign-up request. It never reveals whether an address was already subscribed. */
enum class SubscriptionResult { ConfirmationSent, InvalidEmail, Unavailable }
