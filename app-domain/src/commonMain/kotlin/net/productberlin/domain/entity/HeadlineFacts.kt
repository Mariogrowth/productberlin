package net.productberlin.domain.entity

/**
 * What a headline states plainly: the amount, a deal, a senior role ("CFO", "CO-CEO"), and which of the story's
 * companies it names as an investor ("Zalando backs Sereact"), not as the company raising. Each may be missing.
 */
data class HeadlineFacts(
    val amount: MoneyAmount?,
    val deal: DealKind?,
    val role: String?,
    val investors: Set<String> = emptySet(),
)
