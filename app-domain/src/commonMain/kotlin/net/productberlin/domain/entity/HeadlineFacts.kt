package net.productberlin.domain.entity

/** What a headline states plainly: the amount, a deal, a senior role ("CFO", "CO-CEO"). Each may be missing. */
data class HeadlineFacts(
    val amount: MoneyAmount?,
    val deal: DealKind?,
    val role: String?,
)
