package net.productberlin.domain.entity

/** What a headline says happened, and how much it counts towards a company's press momentum. */
enum class HeadlineEvent(
    val weight: Int,
) {
    /** A funding round, investment or valuation. */
    Funding(3),

    /** A launch, expansion, acquisition, customer, award, revenue milestone or senior hire. */
    Growth(2),

    /** Any other coverage naming the company: interviews, features, commentary. */
    Other(1),

    /** Layoffs, closures, insolvency, lawsuits, hacks or other trouble: reported, but never a reason to rank. */
    Negative(0),

    /** Share-price notes and market columns, published near-daily for listed companies. */
    Market(0),

    /** Promo codes, fee comparisons and consumer reviews about a company's product. */
    Consumer(0),
}
