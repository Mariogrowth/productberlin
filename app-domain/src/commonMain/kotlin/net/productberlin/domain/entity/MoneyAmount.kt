package net.productberlin.domain.entity

/** An amount named in a headline, in millions of its currency ("€" or "$"): "€1.2 billion" is 1,200 in "€". */
data class MoneyAmount(
    val millions: Double,
    val currency: String,
)
